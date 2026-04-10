//
//  NetworkManager.swift
//  TelemetryApp
//

import Foundation
import Network
import UIKit


final class NetworkManager {
    static let shared = NetworkManager()

    // MARK: - Delivery route / stats (local observability)

    enum DeliveryRoute: String, Codable {
        case eu = "EU"
        case ru = "RU"
    }

    struct DeliveryStats: Codable {
        var euBatches: Int = 0
        var ruBatches: Int = 0
        var reportVia: DeliveryRoute? = nil
        var updatedAt: Date = Date()
    }

    private let lastRouteKey = "last_delivery_route_v1"
    private let statsKeyPrefix = "delivery_stats_v1_"

    /// Last successful route used for /ingest (EU or RU).
    /// Persisted to UserDefaults so it survives restarts.
    private(set) var lastDeliveryRoute: DeliveryRoute = .eu {
        didSet {
            UserDefaults.standard.set(lastDeliveryRoute.rawValue, forKey: lastRouteKey)
            NotificationCenter.default.post(
                name: .networkManagerDeliveryRouteDidChange,
                object: nil,
                userInfo: ["route": lastDeliveryRoute.rawValue]
            )
        }
    }
    
    func getDeliveryStats(sessionId: String) -> DeliveryStats {
        loadDeliveryStats(sessionId: sessionId) ?? DeliveryStats()
    }

    // MARK: - Config

    // EU — основной backend
    let euBaseURL = URL(string: "https://api.drivetelemetry.com")!

    // RU — ingress/proxy (fallback, если EU недоступен без VPN/по сети)
    let ruBaseURL = URL(string: "https://ru-api.drivetelemetry.com")!

    
    private let requestTimeout: TimeInterval  = 60
    private let resourceTimeout: TimeInterval = 180
    
    // EU fast-fail session: не ждёт connectivity и быстро таймаутится
    private lazy var euFastFailSession: URLSession = {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.waitsForConnectivity = false
        cfg.timeoutIntervalForRequest  = euFastFailTimeout
        cfg.timeoutIntervalForResource = euFastFailTimeout
        cfg.httpMaximumConnectionsPerHost = 1
        cfg.allowsCellularAccess = true
        return URLSession(configuration: cfg)
    }()
    
    // Быстрый таймаут для EU, чтобы не ждать 20–60 секунд до RU
    private let euFastFailTimeout: TimeInterval = 8.0

    // ingest retry policy (short retry for a single request chain)
    private let maxRetries: Int = 4
    private let baseBackoff: TimeInterval = 1.0

    // finishTrip retry policy (short retry, then pending)
    private let finishMaxRetries: Int = 3

    // MARK: - Logging hook

    var logHandler: ((String) -> Void)?
    
    // MARK: - Network reachability (auto-retry pending finishes)

    private let pathMonitor = NWPathMonitor()
    private let pathQueue = DispatchQueue(label: "drivetelemetry.network.pathmonitor")
    private var lastPathSatisfied: Bool = true

    // Anti-debounce for recovery triggers
    private let recoveryQueue = DispatchQueue(label: "drivetelemetry.network.recovery")
    private var recoveryScheduled: Bool = false
    private var lastRecoveryAt: Date = .distantPast
    private let recoveryMinInterval: TimeInterval = 5.0


    // MARK: - Queue (ingest)

    private let sendQueue = DispatchQueue(label: "drivetelemetry.network.sendqueue")
    private var isUploading = false
    
    struct PendingIngestItem: Codable {
        let id: String          // UUID string
        let createdAt: Date
        let sessionId: String
        let batch: TelemetryBatch
    }

    private var pending: [(PendingIngestItem, ((Result<Void, Error>) -> Void)?)] = []

    // MARK: - Glass game queue (same semantics as ingest)

    struct PendingGlassGameItem: Codable {
        let id: String          // UUID string
        let createdAt: Date
        let sessionId: String
        let batch: GlassGameBatch
    }

    private var pendingGlassGame: [(PendingGlassGameItem, ((Result<Void, Error>) -> Void)?)] = []
    private var isUploadingGlassGame: Bool = false



    // inflight counter for drainIngestQueue
    private let stateQueue = DispatchQueue(label: "drivetelemetry.network.statequeue")
    private var inflightIngestRequests: Int = 0

    private func incInflight() { stateQueue.sync { inflightIngestRequests += 1 } }
    private func decInflight() { stateQueue.sync { inflightIngestRequests = max(0, inflightIngestRequests - 1) } }
    private func getInflight() -> Int { stateQueue.sync { inflightIngestRequests } }
    
    

    /// Max number of pending ingest items kept (disk + memory). Hard cap.
    private let maxPendingBatches = 3000

    /// Directory for persisted ingest queue files.
    private lazy var ingestQueueDir: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("ingest-queue-v1", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()
    
    private func glassGameQueueDir() -> URL {
        let fm = FileManager.default
        let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("glass-game-queue-v1", isDirectory: true)
        if !fm.fileExists(atPath: dir.path) {
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }
    
    private func ingestItemFileURL(id: String) -> URL {
        ingestQueueDir.appendingPathComponent("\(id).json")
    }

    private func persistIngestItem(_ item: PendingIngestItem) {
        let url = ingestItemFileURL(id: item.id)
        do {
            let data = try JSONEncoder().encode(item)
            try data.write(to: url, options: [.atomic])
        } catch {
            logHandler?("[IngestQueue] persist failed id=\(item.id) err=\(error)")
        }
    }

    private func removePersistedIngestItem(id: String) {
        let url = ingestItemFileURL(id: id)
        do {
            try FileManager.default.removeItem(at: url)
        } catch {
            // Remove best-effort; if it fails, it may resend later (idempotency matters)
            logHandler?("[IngestQueue] remove failed id=\(id) err=\(error)")
        }
    }

    /// Load persisted items from disk (sorted by createdAt).
    private func loadPersistedIngestQueue() -> [PendingIngestItem] {
        let fm = FileManager.default
        guard let files = try? fm.contentsOfDirectory(at: ingestQueueDir, includingPropertiesForKeys: nil) else {
            return []
        }

        var items: [PendingIngestItem] = []
        for f in files where f.pathExtension.lowercased() == "json" {
            guard let data = try? Data(contentsOf: f),
                  let item = try? JSONDecoder().decode(PendingIngestItem.self, from: data)
            else {
                // If a file is corrupted, delete it to prevent endless loops
                try? fm.removeItem(at: f)
                continue
            }
            items.append(item)
        }

        items.sort { $0.createdAt < $1.createdAt }
        return items
    }
    
    func loadPersistedGlassGameQueue() -> [PendingGlassGameItem] {
        let fm = FileManager.default
        let dir = glassGameQueueDir()
        guard let files = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else {
            return []
        }

        var items: [PendingGlassGameItem] = []
        for f in files where f.pathExtension.lowercased() == "json" {
            guard let data = try? Data(contentsOf: f),
                  let item = try? JSONDecoder().decode(PendingGlassGameItem.self, from: data)
            else {
                // If a file is corrupted, delete it to prevent endless loops
                try? fm.removeItem(at: f)
                continue
            }
            items.append(item)
        }

        items.sort { $0.createdAt < $1.createdAt }
        return items
    }


    /// Enforce hard cap on persisted queue: drop oldest on disk (and from RAM if already loaded).
    private func enforceIngestQueueCapIfNeeded() {
        dispatchPrecondition(condition: .onQueue(sendQueue))

        // 1) Read all persisted files
        var items = loadPersistedIngestQueue()
        guard items.count > maxPendingBatches else { return }
#if DEBUG
        print("[QUEUE CAP] persisted=\(items.count) > \(maxPendingBatches). Dropping oldest…")
#endif


        let overflow = items.count - maxPendingBatches
        let toDrop = items.prefix(overflow)

        for item in toDrop {
            removePersistedIngestItem(id: item.id)
            // Also remove from in-memory pending if present
            pending.removeAll { $0.0.id == item.id }
        }

        logHandler?("[IngestQueue] overflow cap=\(maxPendingBatches) dropped=\(overflow)")
    }


    // MARK: - Finish pending store (persisted)

    private let finishQueue = DispatchQueue(label: "NetworkManager.finishQueue")
    private let pendingFinishKey = "pending_trip_finishes_v1"
    
    // MARK: - Finish completed guard (in-memory, NOT affecting ingest queue)

    private let finishStateQueue = DispatchQueue(label: "NetworkManager.finishStateQueue")

    /// Session IDs for which we already got a SUCCESS report (2xx) during this app run.
    /// Purpose: cancel duplicate /trip/finish retries (e.g., background retryPendingFinishes) after success.
    private var finishedSessionIds = Set<String>()

    private func markFinishCompleted(_ sessionId: String) {
        finishStateQueue.sync { finishedSessionIds.insert(sessionId) }
    }

    private func isFinishCompleted(_ sessionId: String) -> Bool {
        finishStateQueue.sync { finishedSessionIds.contains(sessionId) }
    }
    
    // MARK: - URLSession

    private let session: URLSession

    private init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.waitsForConnectivity = true
        cfg.timeoutIntervalForRequest  = requestTimeout
        cfg.timeoutIntervalForResource = resourceTimeout
        cfg.httpMaximumConnectionsPerHost = 1
        cfg.allowsCellularAccess = true
        self.session = URLSession(configuration: cfg)

        // Restore last route if present
        if let raw = UserDefaults.standard.string(forKey: lastRouteKey),
           let r = DeliveryRoute(rawValue: raw) {
            self.lastDeliveryRoute = r
        } else {
            self.lastDeliveryRoute = .eu
        }
        startNetworkMonitor()
        
        // Restore persisted ingest queue from disk
        let restored = loadPersistedIngestQueue()

        // One critical rule: all access/mutations of `pending` must happen on sendQueue
        sendQueue.sync {
            // Restore items (no completions after restart)
            self.pending.append(contentsOf: restored.map { ($0, nil) })
            self.sortPendingQueueByPriorityLocked()

            // Apply cap immediately to avoid RAM spike on startup if disk queue is huge
            if self.pending.count > self.maxPendingBatches {
                self.enforceIngestQueueCapIfNeeded()
            }
        }

        // Kick off uploader (async) if we have anything to send
        if !restored.isEmpty {
            sendQueue.async { [weak self] in
                guard let self else { return }
                self.logHandler?("[IngestQueue] restored=\(restored.count) pending=\(self.pending.count)")
                self.kickoffIfNeeded()
            }
        }
        
        // Restore persisted glass-game queue from disk
        let restoredGame = loadPersistedGlassGameQueue()

        sendQueue.sync {
            self.pendingGlassGame.append(contentsOf: restoredGame.map { ($0, nil) })
        }

        if !restoredGame.isEmpty {
            sendQueue.async { [weak self] in
                guard let self else { return }
                self.logHandler?("[GlassGameQueue] restored=\(restoredGame.count) pending=\(self.pendingGlassGame.count)")
                self.drainGlassGameQueue()
            }
        }
                

    }
    
    // MARK: - Auto recovery (pending finishes)

    private func startNetworkMonitor() {
        // Default to "satisfied" until we get the first callback.
        lastPathSatisfied = true

        pathMonitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let satisfied = (path.status == .satisfied)

            // Trigger only on transition: offline -> online
            if satisfied && !self.lastPathSatisfied {
                self.scheduleRecovery(reason: "network restored")
            }

            self.lastPathSatisfied = satisfied
        }

        pathMonitor.start(queue: pathQueue)
    }

    private func scheduleRecovery(reason: String) {
        recoveryQueue.async { [weak self] in
            guard let self else { return }

            let now = Date()

            // anti-debounce: minimum interval between recoveries
            if now.timeIntervalSince(self.lastRecoveryAt) < self.recoveryMinInterval { return }
            if self.recoveryScheduled { return }

            self.recoveryScheduled = true
            self.lastRecoveryAt = now

            // Coalesce multiple triggers into one short-delayed run.
            self.recoveryQueue.asyncAfter(deadline: .now() + 0.4) { [weak self] in
                guard let self else { return }
                self.recoveryScheduled = false

                // 1) drain glass-game queue
                self.drainGlassGameQueue()

                // 2) wait for ingest queue to drain, then 3) retry pending finishes
                self.drainIngestQueue { _ in
                    
                    self.retryPendingFinishes { remaining, lastErr in
                        if remaining > 0 {
                            // ✅ батчи ещё не догнались — повторим чуть позже
                            self.recoveryQueue.asyncAfter(deadline: .now() + 1.5) {
                                self.scheduleRecovery(reason: "pending finishes remaining=\(remaining)")
                            }
                        }

                        if let lastErr = lastErr {
                            self.logHandler?("[Recovery] pending finishes retried; remaining=\(remaining); lastError=\(lastErr); reason=\(reason)")
                        } else {
                            self.logHandler?("[Recovery] pending finishes retried; remaining=\(remaining); reason=\(reason)")
                        }
                    }
                }
            }
        }
    }


    // MARK: - EU primary + RU fallback

    
    private func shouldFallback(statusCode: Int) -> Bool {
        
        // Фоллбэк на RU при “временных” ошибках сервера/шлюза/транзитных проблемах
        if statusCode == 408 || statusCode == 429 { return true }
        if (500...599).contains(statusCode) { return true }
        if statusCode == 502 || statusCode == 503 || statusCode == 504 { return true }
        return false
        
    }

    private func shouldFallback(error: Error) -> Bool {
        let ns = error as NSError
        guard ns.domain == NSURLErrorDomain else { return false }
        switch ns.code {
        case NSURLErrorTimedOut,
            NSURLErrorCannotConnectToHost,
            NSURLErrorNetworkConnectionLost,
            NSURLErrorNotConnectedToInternet,
            NSURLErrorDNSLookupFailed,
            NSURLErrorCannotFindHost,
            NSURLErrorSecureConnectionFailed,
            NSURLErrorInternationalRoamingOff,
            NSURLErrorDataNotAllowed,
            NSURLErrorCannotLoadFromNetwork:
            return true
        default:
            return false
        }
    }


    /// Выполняет запрос к EU. Если EU отвечает транспортной ошибкой (timeout/dns/etc.)
    /// или статусом 408/429/5xx — выполняет такой же запрос к RU.
    /// Возвращает также маршрут (EU/RU), по которому был получен конечный ответ.
    private func performWithFallback(
        makeRequest: @escaping (URL) -> URLRequest,
        completion: @escaping (Result<(DeliveryRoute, HTTPURLResponse, Data), NetworkError>) -> Void
    ) {
        func run(
            route: DeliveryRoute,
            base: URL,
            useFastFail: Bool,
            done: @escaping (Result<(DeliveryRoute, HTTPURLResponse, Data), NetworkError>) -> Void
        ) {
            var req = makeRequest(base)

            // Доп. защита: даже если где-то включён долгий requestTimeout,
            // для fast-fail задаём короткий timeoutInterval на уровне URLRequest.
            if useFastFail {
                req.timeoutInterval = euFastFailTimeout
            }

            let usedSession = useFastFail ? euFastFailSession : session

            let task = usedSession.dataTask(with: req) { [weak self] data, response, error in
                let used = useFastFail ? "EU-fast" : "RU-normal"

                if let error = error as NSError? {
                    self?.logHandler?("[HTTP] \(used) transport error domain=\(error.domain) code=\(error.code) url=\(req.url?.absoluteString ?? "—")")
                    done(.failure(.transport(error: error)))
                    return
                }

                guard let http = response as? HTTPURLResponse else {
                    self?.logHandler?("[HTTP] \(used) invalid response url=\(req.url?.absoluteString ?? "—")")
                    done(.failure(.invalidResponse))
                    return
                }

                // (опционально) логируем статус, чтобы видеть что сервер вообще отвечает
                self?.logHandler?("[HTTP] \(used) status=\(http.statusCode) url=\(req.url?.absoluteString ?? "—") bytes=\(data?.count ?? 0)")

                done(.success((route, http, data ?? Data())))
            }
            task.resume()
        }

        // 1) EU first (FAST FAIL)
        run(route: .eu, base: euBaseURL, useFastFail: true) { [weak self] euResult in
            guard let self else { return }

            switch euResult {
            case .success(let (route, http, data)):
                if self.shouldFallback(statusCode: http.statusCode) {
                #if DEBUG
                logHandler?("[FALLBACK] EU HTTP \(http.statusCode) -> try RU")
                #endif
                    run(route: .ru, base: self.ruBaseURL, useFastFail: false, done: completion)
                } else {
                    completion(.success((route, http, data)))
                }

            case .failure(let err):
                if case .transport(let e) = err {
                    let canFallback = self.shouldFallback(error: e)
                    let ns = e as NSError
                    
                    #if DEBUG
                    logHandler?("[FALLBACK] EU transport error domain=\(ns.domain) code=\(ns.code) canFallback=\(canFallback) -> try RU")
                    #endif
                    
                    if canFallback {
                        run(route: .ru, base: self.ruBaseURL, useFastFail: false, done: completion)
                    } else {
                        completion(.failure(err))
                    }
                } else {
                    completion(.failure(err))
                }
            }
        }
    }

    // MARK: - Drain ingest queue

    /// Ждём, пока:
    /// 1) очередь pending опустеет
    /// 2) inflight запросы завершатся
    ///
    /// Возвращает `true`, если удалось дождаться полного drain до истечения timeout.
    func drainIngestQueue(
        timeout: TimeInterval = 25,
        pollInterval: TimeInterval = 0.25,
        completion: @escaping (_ drained: Bool) -> Void
    ) {
        let start = Date()
        let interval = max(0.05, pollInterval)

        func isDrainedNow() -> Bool {
            let inflight = getInflight()
            let queueEmpty = sendQueue.sync { self.pending.isEmpty && !self.isUploading }
            return inflight == 0 && queueEmpty
        }

        func finish(_ drained: Bool) {
            DispatchQueue.main.async {
                completion(drained)
            }
        }

        func tick() {
            if isDrainedNow() {
                finish(true)
                return
            }

            if Date().timeIntervalSince(start) >= timeout {
                finish(false)
                return
            }

            DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + interval) {
                tick()
            }
        }

        // Start from utility queue to keep work off main
        DispatchQueue.global(qos: .utility).async {
            tick()
        }
    }
    
    private func drainGlassGameQueue() {
        sendQueue.async { [weak self] in
            guard let self else { return }
            guard !self.isUploadingGlassGame else { return }
            guard !self.pendingGlassGame.isEmpty else { return }

            self.isUploadingGlassGame = true
            let (item, completion) = self.pendingGlassGame.removeFirst()

            Task {
                var uploadError: Error? = nil

                do {
                    try await self.postGlassGame(batch: item.batch)
                    self.deleteGlassGameItemFromDisk(itemId: item.id)
                } catch {
                    uploadError = error
                }

                self.sendQueue.async { [weak self] in
                    guard let self else { return }

                    if let err = uploadError {
                        // requeue to the end + keep on disk
                        self.pendingGlassGame.append((item, completion))
                        completion?(.failure(err))
                    } else {
                        completion?(.success(()))
                    }

                    self.isUploadingGlassGame = false

                    // continue draining until empty
                    if !self.pendingGlassGame.isEmpty {
                        self.drainGlassGameQueue()
                    }
                }
            }
        }
    }

    private func deleteGlassGameItemFromDisk(itemId: String) {
        let url = glassGameQueueDir().appendingPathComponent("\(itemId).json")
        try? FileManager.default.removeItem(at: url)
    }


    // MARK: - Public API: Upload batch (/ingest)

    func upload(
        batch: TelemetryBatch,
        completion: ((Result<Void, Error>) -> Void)? = nil
    ) {
        sendQueue.async { [weak self] in
            guard let self else { return }

            let item = PendingIngestItem(
                id: UUID().uuidString,
                createdAt: Date(),
                sessionId: batch.session_id,
                batch: batch
            )

            // 1) Persist first (so we survive crash/kill)
            self.persistIngestItem(item)

            // 2) Enqueue in memory
            self.pending.append((item, completion))
            self.sortPendingQueueByPriorityLocked()

            // 3) RAM warning (after append so it's accurate)
            if self.pending.count >= self.maxPendingBatches {
                self.logHandler?("[IngestQueue] RAM pending reached hard cap count=\(self.pending.count) cap=\(self.maxPendingBatches)")
            }

            // 4) Enforce hard cap (disk + memory) only when we actually overflow
            if self.pending.count > self.maxPendingBatches {
                self.enforceIngestQueueCapIfNeeded()
            }

            // 5) Kickoff uploader
            self.kickoffIfNeeded()
        }
    }
    
    private func enqueueGlassGameItem(_ item: PendingGlassGameItem, completion: ((Result<Void, Error>) -> Void)?) {
        pendingGlassGame.append((item, completion))
        persistGlassGameItem(item)
    }

    private func persistGlassGameItem(_ item: PendingGlassGameItem) {
        let dir = glassGameQueueDir()
        let url = dir.appendingPathComponent("\(item.id).json")
        do {
            let data = try JSONEncoder().encode(item)
            try data.write(to: url, options: .atomic)
        } catch {
            logHandler?("persistGlassGameItem failed: \(error)")
        }
    }

    
    func uploadGlassGame(batch: GlassGameBatch, sessionId: String, completion: ((Result<Void, Error>) -> Void)? = nil) {
        let item = PendingGlassGameItem(
            id: UUID().uuidString,
            createdAt: Date(),
            sessionId: sessionId,
            batch: batch
        )
        enqueueGlassGameItem(item, completion: completion)
        drainGlassGameQueue()
    }





    private func kickoffIfNeeded() {
        guard !isUploading, !pending.isEmpty else { return }

        sortPendingQueueByPriorityLocked()

        isUploading = true
        let (item, completion) = pending.removeFirst()

        /// Completion is intended for UI signaling only.
        /// Heavy work must NOT be done here (called on main thread).

        performUpload(batch: item.batch, attempt: 0) { [weak self] result in
            guard let self else { return }

            // 1) Notify caller (if any) — on main queue
            if let completion {
                DispatchQueue.main.async {
                    completion(result)
                }
            }

            // 2) Queue bookkeeping + disk cleanup
            self.sendQueue.async {
                if case .success = result {
                    self.removePersistedIngestItem(id: item.id)
                }
                self.isUploading = false
                self.kickoffIfNeeded()
            }
        }
    }



    func ensureBearerWithFallback(deviceId: String) async throws -> String {
        do {
            self.logHandler?("[AUTH] bearer route=EU start")
            let token = try await AuthManager.shared.ensureToken(baseURL: euBaseURL, deviceId: deviceId)
            self.logHandler?("[AUTH] bearer route=EU success")
            return token
        } catch {
            let ns = error as NSError
            self.logHandler?("[AUTH] bearer route=EU failed domain=\(ns.domain) code=\(ns.code) -> fallback RU")
            do {
                let token = try await AuthManager.shared.ensureToken(baseURL: ruBaseURL, deviceId: deviceId)
                self.logHandler?("[AUTH] bearer route=RU success")
                return token
            } catch {
                let ns = error as NSError
                self.logHandler?("[AUTH] bearer route=RU failed domain=\(ns.domain) code=\(ns.code)")
                throw error
            }
        }
    }
    
    

    
    private func performUpload(
        batch: TelemetryBatch,
        attempt: Int,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        // Encode once; reuse for EU/RU within one attempt chain
        let bodyData: Data
        do {
            bodyData = try JSONEncoder().encode(batch)
        } catch {
            DispatchQueue.main.async { completion(.failure(error)) }
            return
        }
        
#if DEBUG
if attempt == 0 {
    if let json = String(data: bodyData, encoding: .utf8) {
        let message = "[INGEST] batch payload:\n\(json)"
        
        print(message)          // <-- вывод в консоль
        logHandler?(message)    // <-- если где-то подключён UI лог
    } else {
        let message = "[INGEST] batch payload: <non-utf8 \(bodyData.count) bytes>"
        print(message)
        logHandler?(message)
    }
}
#endif
                
        incInflight()

        Task { [weak self] in
            guard let self else { return }
            defer { self.decInflight() }

            do {
                // 1) Bearer (EU first, then RU fallback inside ensureBearerWithFallback if needed)
                let bearer = try await self.ensureBearerWithFallback(deviceId: batch.device_id)

                // 2) Upload with EU/RU fallback
                self.performWithFallback(
                    makeRequest: { [weak self] base in
                        guard let self else { return URLRequest(url: base) }

                       
                        // LOG выбранного baseURL на каждой попытке
                        if base.host == self.ruBaseURL.host {
                            self.logRoute("/ingest", baseURL: base, attempt: attempt, note: "FALLBACK EU->RU (selected base)")
                        } else {
                            self.logRoute("/ingest", baseURL: base, attempt: attempt)

                        }


                        let url = base.appendingPathComponent("ingest")
                        return self.makeRequest(url: url, body: bodyData, bearerToken: bearer)
                    },
                    completion: { [weak self] result in
                        guard let self else { return }

                        switch result {
                            
                        case .failure(let netErr):
                            if attempt < self.maxRetries {
                                
                                self.scheduleRetry(
                                    attempt: attempt,
                                    reason: String(describing: netErr)
                                ) {
                                    self.performUpload(batch: batch, attempt: attempt + 1, completion: completion)
                                }
                                return
                            }
                            DispatchQueue.main.async { completion(.failure(netErr)) }
                            return

                        case .success(let (route, http, data)):
                            // Keep idempotency: 409 == success
                            if http.statusCode == 409 {
                                self.onBatchDelivered(sessionId: batch.session_id, route: route)
                                self.publishIngestTotalsIfPresent(data: data, fallbackSessionId: batch.session_id)

                                DispatchQueue.main.async { completion(.success(())) }
                                return
                            }

                            if (200...299).contains(http.statusCode) {
                                self.onBatchDelivered(sessionId: batch.session_id, route: route)
                                self.publishIngestTotalsIfPresent(data: data, fallbackSessionId: batch.session_id)

                                DispatchQueue.main.async { completion(.success(())) }
                                return
                            }

                            let bodyStr = String(data: data, encoding: .utf8) ?? ""
                            let httpErr = httpStatus(code: http.statusCode, body: bodyStr)
                            
                            // If token expired / invalid -> clear auth and retry THIS ingest once
                            if http.statusCode == 401, attempt < self.maxRetries {
                                #if DEBUG
                                self.logHandler?("[INGEST] got 401 -> clearing auth and retrying")
                                #endif

                                AuthManager.shared.clearAllAuthState()

                                self.scheduleRetry(
                                    attempt: attempt,
                                    reason: "HTTP 401 (reset auth)"
                                ) { [weak self] in
                                    self?.performUpload(batch: batch, attempt: attempt + 1, completion: completion)
                                }
                                return
                            }




                            if self.shouldRetry(statusCode: http.statusCode), attempt < self.maxRetries {
                                self.scheduleRetry(attempt: attempt, reason: "HTTP \(http.statusCode)") {
                                    self.performUpload(batch: batch, attempt: attempt + 1, completion: completion)
                                }
                                return
                            }

                            DispatchQueue.main.async { completion(.failure(httpErr)) }
                        }
                    }
                )
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }
    
    // MARK: - Trips archive (last 10 trips)

    func fetchRecentTrips(
        deviceId: String,
        driverId: String,
        limit: Int = 30,
        completion: @escaping (Result<[TripSummary], Error>) -> Void
    ) {
        Task {
            do {
                let bearer = try await self.ensureBearerWithFallback(deviceId: deviceId)

                self.performWithFallback(
                    makeRequest: { base in
                        var url = base.appendingPathComponent("trips/recent")
                        var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)!
                        comps.queryItems = [
                            URLQueryItem(name: "driver_id", value: driverId),
                            URLQueryItem(name: "limit", value: String(max(1, min(30, limit))))
                        ]
                        url = comps.url!

                        var req = URLRequest(url: url)
                        req.httpMethod = "GET"
                        req.timeoutInterval = 20
                        req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
                        return req
                    },
                    completion: { result in
                        switch result {
                        case .failure(let e):
                            completion(.failure(e))
                        case .success((_, let http, let data)):
                            guard (200...299).contains(http.statusCode) else {
                                let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
                                completion(.failure(NSError(domain: "HTTP", code: http.statusCode,
                                                            userInfo: [NSLocalizedDescriptionKey: msg])))
                                return
                            }
                            do {
                                let decoded = try JSONDecoder().decode(RecentTripsResponse.self, from: data)
                                completion(.success(decoded.trips))
                            } catch {
                                completion(.failure(error))
                            }
                        }
                    }
                )
            } catch {
                completion(.failure(error))
            }
        }
    }
    
    func fetchDriverHome(
        deviceId: String,
        driverId: String?,
        completion: @escaping (Result<DriverHomeResponse, Error>) -> Void
    ) {
        Task {
            do {
                let bearer = try await ensureBearerWithFallback(deviceId: deviceId)

                self.performWithFallback(
                    makeRequest: { base in
                        var url = base.appendingPathComponent("driver/home")

                        let trimmedDriverId = driverId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        if !trimmedDriverId.isEmpty {
                            var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)!
                            comps.queryItems = [
                                URLQueryItem(name: "driver_id", value: trimmedDriverId)
                            ]
                            url = comps.url!
                        }

                        var req = URLRequest(url: url)
                        req.httpMethod = "GET"
                        req.timeoutInterval = 20
                        req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
                        return req
                    },
                    completion: { result in
                        switch result {
                        case .failure(let e):
                            completion(.failure(e))

                        case .success((_, let http, let data)):
                            guard (200...299).contains(http.statusCode) else {
                                let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
                                completion(.failure(NSError(
                                    domain: "HTTP",
                                    code: http.statusCode,
                                    userInfo: [NSLocalizedDescriptionKey: msg]
                                )))
                                return
                            }

                            do {
                                let decoded = try JSONDecoder().decode(DriverHomeResponse.self, from: data)
                                completion(.success(decoded))
                            } catch {
                                completion(.failure(error))
                            }
                        }
                    }
                )
            } catch {
                completion(.failure(error))
            }
        }
    }

    func fetchTripReport(
        deviceId: String,
        sessionId: String,
        driverId: String,
        completion: @escaping (Result<TripReport, Error>) -> Void
    ) {
        Task {
            do {
                let bearer = try await self.ensureBearerWithFallback(deviceId: deviceId)

                self.performWithFallback(
                    makeRequest: { base in
                        var url = base.appendingPathComponent("trip/report")
                        var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)!
                        comps.queryItems = [
                            URLQueryItem(name: "session_id", value: sessionId),
                            URLQueryItem(name: "driver_id", value: driverId)
                        ]
                        url = comps.url!

                        var req = URLRequest(url: url)
                        req.httpMethod = "GET"
                        req.timeoutInterval = 20
                        req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
                        return req
                    },
                    completion: { result in
                        switch result {
                        case .failure(let e):
                            completion(.failure(e))
                        case .success((_, let http, let data)):
                            guard (200...299).contains(http.statusCode) else {
                                let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
                                completion(.failure(NSError(domain: "HTTP", code: http.statusCode,
                                                            userInfo: [NSLocalizedDescriptionKey: msg])))
                                return
                            }
                            do {
                                let report = try JSONDecoder().decode(TripReport.self, from: data)
                                completion(.success(report))
                            } catch {
                                completion(.failure(error))
                            }
                        }
                    }
                )
            } catch {
                completion(.failure(error))
            }
        }
    }


    // MARK: - Trip finish (report)

    func finishTrip(
        sessionId: String,
        driverId: String,
        deviceId: String,
        trackingMode: String? = nil,
        transportMode: String? = nil,
        clientEndedAt: String? = nil,
        tripDurationSec: Double? = nil,
        finishReason: String? = nil,
        clientMetrics: ClientTripMetrics? = nil,
        deviceContext: [String: Any]? = nil,
        
        tailActivityContext: [String: Any]? = nil,

        completion: @escaping (Result<TripReport, Error>) -> Void
    ) {
        let effectiveDriverId = resolvedDriverId(driverId)

        let endedAtISO = clientEndedAt ?? ISO8601DateFormatter().string(from: Date())
        
        let pending = PendingTripFinish(
            session_id: sessionId,
            driver_id: effectiveDriverId,
            device_id: deviceId,
            client_ended_at: endedAtISO,
            created_at: ISO8601DateFormatter().string(from: Date()),
            tracking_mode: trackingMode,
            transport_mode: transportMode,
            trip_duration_sec: tripDurationSec,
            finish_reason: finishReason,
            client_metrics: clientMetrics,
            device_context_json: encodeJSONObjectString(deviceContext),
            tail_activity_context_json: encodeJSONObjectString(tailActivityContext),

            app_version: appVersion(),
            app_build: appBuild(),
            ios_version: iosVersion(),
            device_model: deviceModelIdentifier(),
            locale: localeId(),
            timezone: timeZoneId()
        )
        
        // === IMPORTANT: do not send finish until at least one batch is DELIVERED ===
        let stats = getDeliveryStats(sessionId: sessionId)
        let deliveredBatches = stats.euBatches + stats.ruBatches
        
        if deliveredBatches == 0 {

            upsertPendingFinish(pending)

            self.scheduleRecovery(reason: "finish queued (no delivered batches yet)")

            let queuedReport = TripReport.queued(
                sessionId: sessionId,
                driverId: driverId,
                deviceId: deviceId
            )

            DispatchQueue.main.async {
                completion(.success(queuedReport))
            }

            return
        }


        performFinishTrip(pending: pending, attempt: 0, storePendingOnFailure: true, completion: completion)
    }
    
    private func encodeJSONObjectString(_ value: [String: Any]?) -> String? {
        guard let value else { return nil }
        guard JSONSerialization.isValidJSONObject(value) else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: value, options: []) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func decodeJSONObjectString(_ json: String?) -> [String: Any]? {
        guard let json, let data = json.data(using: .utf8) else { return nil }
        guard let obj = try? JSONSerialization.jsonObject(with: data, options: []) else { return nil }
        return obj as? [String: Any]
    }



    /// Mark finish as pending locally (no network request).
    func markFinishPending(
        sessionId: String,
        driverId: String,
        deviceId: String,
        trackingMode: String? = nil,
        transportMode: String? = nil,
        clientEndedAt: String? = nil,
        tripDurationSec: Double? = nil,
        finishReason: String? = nil,
        clientMetrics: ClientTripMetrics? = nil,
        deviceContext: [String: Any]? = nil,
        tailActivityContext: [String: Any]? = nil
    ){
        let endedAtISO = clientEndedAt ?? ISO8601DateFormatter().string(from: Date())

        let effectiveDriverId = resolvedDriverId(driverId)

        let p = PendingTripFinish(
            session_id: sessionId,
            driver_id: effectiveDriverId,
            device_id: deviceId,
            client_ended_at: endedAtISO,
            created_at: ISO8601DateFormatter().string(from: Date()),
            tracking_mode: trackingMode,
            transport_mode: transportMode,
            trip_duration_sec: tripDurationSec,
            finish_reason: finishReason,
            client_metrics: clientMetrics,
            device_context_json: encodeJSONObjectString(deviceContext),
            tail_activity_context_json: encodeJSONObjectString(tailActivityContext),
            

            app_version: appVersion(),
            app_build: appBuild(),
            ios_version: iosVersion(),
            device_model: deviceModelIdentifier(),
            locale: localeId(),
            timezone: timeZoneId()
        )
        upsertPendingFinish(p)
    }



    func pendingFinishCount() -> Int {
        loadPendingFinishes().count
    }

    /// Retry all pending finishes sequentially.
    // Now first go sessions from the last trip - not to wait long for trip report
    
    func retryPendingFinishes(completion: ((Int, String?) -> Void)? = nil) {
        finishQueue.async {
            var items = self.loadPendingFinishes()

            guard !items.isEmpty else {
                DispatchQueue.main.async {
                    completion?(0, nil)
                }
                return
            }

            items.sort(by: { (lhs: PendingTripFinish, rhs: PendingTripFinish) in
                if lhs.session_id == rhs.session_id {
                    return lhs.created_at < rhs.created_at
                }

                let lStats = self.getDeliveryStats(sessionId: lhs.session_id)
                let rStats = self.getDeliveryStats(sessionId: rhs.session_id)

                let lDelivered = lStats.euBatches + lStats.ruBatches
                let rDelivered = rStats.euBatches + rStats.ruBatches

                if (lDelivered > 0) != (rDelivered > 0) {
                    return lDelivered > 0
                }

                return lhs.created_at < rhs.created_at
            })

            let sem = DispatchSemaphore(value: 0)
            var remaining = items.count
            var lastErr: String?

            for p in items {
                let stats = self.getDeliveryStats(sessionId: p.session_id)
                let deliveredBatches = stats.euBatches + stats.ruBatches

                if deliveredBatches == 0 {
                    lastErr = "pending finish waiting for first delivered batch (session=\(p.session_id))"
                    continue
                }

                self.performFinishTrip(
                    pending: p,
                    attempt: 0,
                    storePendingOnFailure: false
                ) { result in
                    switch result {
                    case .success:
                        remaining -= 1

                    case .failure(let err):
                        lastErr = err.localizedDescription
                    }
                    sem.signal()
                }

                sem.wait()
            }

            DispatchQueue.main.async {
                completion?(remaining, lastErr)
            }
        }
    }

    private func isNoBatchesYet(_ body: String) -> Bool {
        body.lowercased().contains("no batches")
    }
    
    private func extractDetailCode(_ body: String) -> String? {
        // ожидаем {"detail":"NO_BATCHES_YET"} или просто текст
        if body.isEmpty { return nil }
        if let data = body.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data, options: []),
           let dict = obj as? [String: Any],
           let d = dict["detail"] as? String {
            return d.uppercased()
        }
        return body.uppercased()
    }

    private func isEarlyFinishCode(_ detailCode: String?) -> Bool {
        guard let dc = detailCode?.uppercased() else { return false }
        return dc.contains("NO_BATCHES_YET") || dc.contains("SESSION_MISSING") || dc.contains("NO_DATA_YET")
    }

    // “ранний финиш” = сервер принял finish, но данных ещё нет (RU->EU лаг)
    // - 202 с пустым отчётом (твой кейс)
    // - 503 с detail-кодами (новый бэкенд-кейс)
    private func shouldTreatAsQueuedFinish(statusCode: Int, body: String, decodedReport: TripReport?) -> Bool {
        if statusCode == 503 {
            let dc = extractDetailCode(body)
            return isEarlyFinishCode(dc)
        }

        if statusCode == 202 {
            return true
        }

        if let r = decodedReport {
            let empty = (r.batches_count == 0) && (r.samples_count == 0) && (r.events_count == 0)
            if empty { return true }
        }

        return false
    }

    private func queuedFinishUserMessage(sessionId: String, statusCode: Int, body: String) -> String {
        if statusCode == 503 {
            let dc = extractDetailCode(body) ?? "NO_DATA_YET"
            return "finish queued: \(dc) (session=\(sessionId))"
        }
        return "finish queued: ingest not caught up yet (session=\(sessionId))"
    }
    
    private func sanitized(_ m: ClientTripMetrics) -> ClientTripMetrics {
        func agg(_ a: ClientAgg) -> ClientAgg {
            ClientAgg(
                count: a.count,
                sum_intensity: NumericSanitizer.metric(a.sum_intensity),
                max_intensity: NumericSanitizer.metric(a.max_intensity),
                count_per_km: NumericSanitizer.metric(a.count_per_km),
                sum_per_km: NumericSanitizer.metric(a.sum_per_km)
            )
        }

        return ClientTripMetrics(
            trip_distance_m: NumericSanitizer.metric(m.trip_distance_m),
            trip_distance_km_from_gps: NumericSanitizer.metric(m.trip_distance_km_from_gps),
            brake: agg(m.brake),
            accel: agg(m.accel),
            road: agg(m.road),
            turn: agg(m.turn)
        )
    }
    
    // Public Alpha additive fields
    private func publicAlphaSummary(from metrics: ClientTripMetrics, durationSec: Double?) -> [String: Any] {
        func load(_ agg: ClientAgg) -> Double {
            guard agg.count > 0 else { return 0.0 }
            let mean = agg.sum_intensity / Double(agg.count)
            return NumericSanitizer.metric(Double(agg.count) * mean * mean)
        }

        let distanceKm = max(0.001, metrics.trip_distance_km_from_gps)
        let tripLoad = load(metrics.brake) + load(metrics.accel) + load(metrics.turn) + load(metrics.road)
        let drivingLoad = NumericSanitizer.metric(tripLoad / distanceKm)
        let scoreV2 = NumericSanitizer.metric(100.0 * exp(-0.15 * drivingLoad), digits: 2)

        let avgSpeedKmh: Double = {
            guard let durationSec, durationSec > 0 else { return 0.0 }
            let hours = durationSec / 3600.0
            guard hours > 0 else { return 0.0 }
            return NumericSanitizer.metric(distanceKm / hours, digits: 2)
        }()

        let drivingMode: String = {
            if avgSpeedKmh >= 60 { return "Highway" }
            if avgSpeedKmh > 0 && avgSpeedKmh <= 35 { return "City" }
            return "Mixed"
        }()

        return [
            "score_v2": scoreV2,
            "driving_load": drivingLoad,
            "distance_km": NumericSanitizer.metric(distanceKm),
            "avg_speed_kmh": avgSpeedKmh,
            "driving_mode": drivingMode,
            "trip_duration_sec": NumericSanitizer.metric(durationSec ?? 0.0)
        ]
    }


    private func performFinishTrip(
        pending: PendingTripFinish,
        attempt: Int,
        storePendingOnFailure: Bool,
        completion: @escaping (Result<TripReport, Error>) -> Void
    ) {
        // EARLY EXIT: already got SUCCESS for this session in this app run
        if isFinishCompleted(pending.session_id) {
            DispatchQueue.main.async {
                completion(.failure(httpStatus(code: 208, body: "finish already completed locally (noop)")))
            }
            return
        }

        func isFinishNotReady503(_ body: String) -> Bool {
            let s = body.uppercased()
            return s.contains("SESSION_MISSING") || s.contains("NO_BATCHES_YET") || s.contains("NO_DATA_YET")
        }

        func queuePendingAndRetry(_ reason: String, _ visibleStatus: Int, _ visibleBody: String) {
            if storePendingOnFailure {
                self.upsertPendingFinish(pending)
            }

            // ✅ запускаем авто-восстановление сразу
            self.scheduleRecovery(reason: reason)

            // ✅ планируем retry (не блокируя UI)
            if attempt < self.finishMaxRetries {
                self.scheduleRetry(
                    attempt: attempt,
                    reason: reason,
                    cancelIf: { [weak self] in self?.isFinishCompleted(pending.session_id) == true }
                ) { [weak self] in
                    self?.performFinishTrip(
                        pending: pending,
                        attempt: attempt + 1,
                        storePendingOnFailure: storePendingOnFailure,
                        completion: completion
                    )
                }
            }

            DispatchQueue.main.async {
                completion(.failure(httpStatus(code: visibleStatus, body: visibleBody)))
            }
        }

        // Encode once; reuse for EU/RU within one attempt chain
        let bodyData: Data
        do {
            var payload: [String: Any] = [
                "session_id": pending.session_id,
                "driver_id": pending.driver_id,
                "device_id": pending.device_id,
                "client_ended_at": pending.client_ended_at
            ]
            
            // Public Alpha additive fields
            payload["trip_core"] = [
                "trip_id": pending.session_id,
                "session_id": pending.session_id,
                
                "client_ended_at": pending.client_ended_at as Any
            ]
            
            // Public Alpha additive fields
            payload["device_meta"] = [
                "platform": "iOS",
                "app_version": pending.app_version as Any,
                "app_build": pending.app_build as Any,
                "ios_version": pending.ios_version as Any,
                "device_model": pending.device_model as Any,
                "locale": pending.locale as Any,
                "timezone": pending.timezone as Any
            ]
            
            // Public Alpha additive fields
            if let deviceContext = decodeJSONObjectString(pending.device_context_json) {
                payload["device_context"] = deviceContext
            }
            
            if let tailActivityContext = decodeJSONObjectString(pending.tail_activity_context_json) {
                payload["tail_activity_context"] = tailActivityContext
            }
                        
            if let trackingMode = pending.tracking_mode { payload["tracking_mode"] = trackingMode }
            if let transportMode = pending.transport_mode { payload["transport_mode"] = transportMode }
            if let duration = pending.trip_duration_sec {
                payload["trip_duration_sec"] = NumericSanitizer.metric(duration)
            }
            if let v = pending.finish_reason { payload["finish_reason"] = v }

            if let v = pending.app_version { payload["app_version"] = v }
            if let v = pending.app_build { payload["app_build"] = v }
            if let v = pending.ios_version { payload["ios_version"] = v }
            if let v = pending.device_model { payload["device_model"] = v }
            if let v = pending.locale { payload["locale"] = v }
            if let v = pending.timezone { payload["timezone"] = v }
            
            if let cm = pending.client_metrics {
                let safe = sanitized(cm)

                if let data = try? JSONEncoder().encode(safe),
                   let obj = try? JSONSerialization.jsonObject(with: data, options: []) {
                    payload["client_metrics"] = obj

                    // Public Alpha additive fields
                    payload["trip_summary"] = publicAlphaSummary(
                        from: safe,
                        durationSec: pending.trip_duration_sec
                    )

                    // Public Alpha additive fields
                    payload["trip_metrics_raw"] = [
                        "trip_distance_m": safe.trip_distance_m,
                        "trip_distance_km_from_gps": safe.trip_distance_km_from_gps,
                        "brake": [
                            "count": safe.brake.count,
                            "sum_intensity": safe.brake.sum_intensity,
                            "max_intensity": safe.brake.max_intensity,
                            "count_per_km": safe.brake.count_per_km,
                            "sum_per_km": safe.brake.sum_per_km
                        ],
                        "accel": [
                            "count": safe.accel.count,
                            "sum_intensity": safe.accel.sum_intensity,
                            "max_intensity": safe.accel.max_intensity,
                            "count_per_km": safe.accel.count_per_km,
                            "sum_per_km": safe.accel.sum_per_km
                        ],
                        "turn": [
                            "count": safe.turn.count,
                            "sum_intensity": safe.turn.sum_intensity,
                            "max_intensity": safe.turn.max_intensity,
                            "count_per_km": safe.turn.count_per_km,
                            "sum_per_km": safe.turn.sum_per_km
                        ],
                        "road": [
                            "count": safe.road.count,
                            "sum_intensity": safe.road.sum_intensity,
                            "max_intensity": safe.road.max_intensity,
                            "count_per_km": safe.road.count_per_km,
                            "sum_per_km": safe.road.sum_per_km
                        ]
                    ]
                }
            }

            bodyData = try JSONSerialization.data(withJSONObject: payload, options: [])

#if DEBUG
if let json = String(data: bodyData, encoding: .utf8) {
    print("[FINISH] payload:\n\(json)")
    logHandler?("[FINISH] payload:\n\(json)")
} else {
    print("[FINISH] payload: <non-utf8 \(bodyData.count) bytes>")
    logHandler?("[FINISH] payload: <non-utf8 \(bodyData.count) bytes>")
}
#endif
        } catch {
            DispatchQueue.main.async { completion(.failure(error)) }
            return
        }

        incInflight()

        Task { [weak self] in
            guard let self else { return }
            defer { self.decInflight() }

            do {
                let bearer = try await self.ensureBearerWithFallback(deviceId: pending.device_id)

                self.performWithFallback(
                    makeRequest: { [weak self] base in
                        guard let self else { return URLRequest(url: base) }

                        if base.host == self.ruBaseURL.host {
                            self.logRoute("/trip/finish", baseURL: base, attempt: attempt, note: "FALLBACK EU->RU (selected base)", sessionId: pending.session_id)
                        } else {
                            self.logRoute("/trip/finish", baseURL: base, attempt: attempt, sessionId: pending.session_id)
                        }

                        let url = base.appendingPathComponent("trip/finish")
                        return self.makeRequest(url: url, body: bodyData, bearerToken: bearer)
                    },
                    completion: { [weak self] result in
                        guard let self else { return }

                        switch result {
                        case .failure(let netErr):
                            // network-level retry
                            if attempt < self.finishMaxRetries {
                                if self.isFinishCompleted(pending.session_id) {
                                    DispatchQueue.main.async {
                                        completion(.failure(httpStatus(code: 409, body: "finish already completed locally")))
                                    }
                                    return
                                }

                                self.scheduleRetry(
                                    attempt: attempt,
                                    reason: String(describing: netErr),
                                    cancelIf: { [weak self] in self?.isFinishCompleted(pending.session_id) == true }
                                ) {
                                    self.performFinishTrip(
                                        pending: pending,
                                        attempt: attempt + 1,
                                        storePendingOnFailure: storePendingOnFailure,
                                        completion: completion
                                    )
                                }
                                return
                            }

                            if storePendingOnFailure { self.upsertPendingFinish(pending) }
                            DispatchQueue.main.async { completion(.failure(netErr)) }

                        case .success(let (route, http, data)):
                            let bodyStr = String(data: data, encoding: .utf8) ?? ""
                            let httpErr = httpStatus(code: http.statusCode, body: bodyStr)

                            // 401 -> clear auth and retry
                            if http.statusCode == 401, attempt < self.finishMaxRetries {
                                #if DEBUG
                                self.logHandler?("[FINISH] got 401 -> clearing auth and retrying")
                                #endif

                                AuthManager.shared.clearAllAuthState()

                                self.scheduleRetry(attempt: attempt, reason: "HTTP 401 (reset auth)") {
                                    self.performFinishTrip(
                                        pending: pending,
                                        attempt: attempt + 1,
                                        storePendingOnFailure: storePendingOnFailure,
                                        completion: completion
                                    )
                                }
                                return
                            }

                            // 202 Accepted -> keep pending + recovery + retry
                            if http.statusCode == 202 {
                                queuePendingAndRetry(
                                    "HTTP 202 (finish queued / not ready)",
                                    202,
                                    "finish queued; will retry (session=\(pending.session_id))"
                                )
                                return
                            }

                            // 503 with SESSION_MISSING/NO_BATCHES_YET/NO_DATA_YET -> keep pending + recovery + retry
                            if http.statusCode == 503, isFinishNotReady503(bodyStr) {
                                queuePendingAndRetry(
                                    "HTTP 503 NOT_READY: \(bodyStr)",
                                    503,
                                    bodyStr.isEmpty ? "finish not ready; will retry (session=\(pending.session_id))" : bodyStr
                                )
                                return
                            }

                            // 404 NO_BATCHES_YET -> keep pending + recovery + retry
                            if http.statusCode == 404, self.isNoBatchesYet(bodyStr) {
                                queuePendingAndRetry(
                                    "HTTP 404 NO_BATCHES_YET",
                                    404,
                                    bodyStr.isEmpty ? "no batches yet; will retry (session=\(pending.session_id))" : bodyStr
                                )
                                return
                            }

                            // 2xx success -> decode report
                            if (200...299).contains(http.statusCode) {
                                DispatchQueue.global(qos: .userInitiated).async {
                                    Task { @MainActor in
                                        do {
                                            let report = try JSONDecoder().decode(TripReport.self, from: data)

                                            let isEarlyEmpty =
                                                (report.batches_count == 0) &&
                                                (report.samples_count == 0) &&
                                                (report.events_count == 0)

                                            if isEarlyEmpty {
                                                queuePendingAndRetry(
                                                    "2xx but empty report (ingest not caught up yet)",
                                                    202,
                                                    "finish accepted but report empty; will retry (session=\(pending.session_id))"
                                                )
                                                return
                                            }

                                            self.markFinishCompleted(pending.session_id)
                                            self.recordReportDelivery(sessionId: pending.session_id, route: route)
                                            self.removePendingFinish(sessionId: pending.session_id)

                                            completion(.success(report))
                                        } catch {
                                            if storePendingOnFailure { self.upsertPendingFinish(pending) }
                                            completion(.failure(error))
                                        }
                                    }
                                }
                                return
                            }

                            // generic retry for retryable codes
                            if self.shouldRetry(statusCode: http.statusCode), attempt < self.finishMaxRetries {
                                if self.isFinishCompleted(pending.session_id) { return }

                                self.scheduleRetry(
                                    attempt: attempt,
                                    reason: "HTTP \(http.statusCode)",
                                    cancelIf: { [weak self] in self?.isFinishCompleted(pending.session_id) == true }
                                ) {
                                    self.performFinishTrip(
                                        pending: pending,
                                        attempt: attempt + 1,
                                        storePendingOnFailure: storePendingOnFailure,
                                        completion: completion
                                    )
                                }
                                return
                            }

                            // final failure
                            if storePendingOnFailure { self.upsertPendingFinish(pending) }
                            DispatchQueue.main.async { completion(.failure(httpErr)) }
                        }
                    }
                )
            } catch {
                if storePendingOnFailure { self.upsertPendingFinish(pending) }
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }
    
    private func logRoute(
        _ path: String,
        baseURL: URL,
        attempt: Int,
        note: String? = nil,
        sessionId: String? = nil
    ) {
        #if DEBUG
        let host = baseURL.host ?? "(no-host)"
        var msg = "[ROUTE] \(path) -> \(host) attempt=\(attempt)"

        if let sessionId {
            msg += " session=\(sessionId)"
        }
        if let note {
            msg += " note=\(note)"
        }

        logHandler?(msg)
        #endif
    }

    
    



    // MARK: - Local delivery stats helpers

    private func statsKey(sessionId: String) -> String {
        statsKeyPrefix + sessionId
    }

    private func loadDeliveryStats(sessionId: String) -> DeliveryStats? {
        guard let data = UserDefaults.standard.data(forKey: statsKey(sessionId: sessionId)) else { return nil }
        return try? JSONDecoder().decode(DeliveryStats.self, from: data)
    }

    private func saveDeliveryStats(sessionId: String, _ stats: DeliveryStats) {
        if let data = try? JSONEncoder().encode(stats) {
            UserDefaults.standard.set(data, forKey: statsKey(sessionId: sessionId))
        }
    }

    private func recordBatchDelivery(sessionId: String, route: DeliveryRoute) {
        lastDeliveryRoute = route

        var s = loadDeliveryStats(sessionId: sessionId) ?? DeliveryStats()
                
        switch route {
        case .eu: s.euBatches += 1
        case .ru: s.ruBatches += 1
        }
        s.updatedAt = Date()
        saveDeliveryStats(sessionId: sessionId, s)
        
        let delivered = s.euBatches + s.ruBatches
        NotificationCenter.default.post(
            name: .networkManagerDeliveryStatsDidUpdate,
            object: nil,
            userInfo: [
                "session_id": sessionId,
                "euBatches": s.euBatches,
                "ruBatches": s.ruBatches,
                "delivered": delivered
            ]
        )

    }
    
    private func postGlassGame(batch: GlassGameBatch) async throws {
        // токен получаем так же, как для основной телеметрии (EU -> RU fallback внутри)
        let bearer = try await ensureBearerWithFallback(deviceId: batch.device_id)

        // кодируем один раз, чтобы одинаковый payload ушёл и в EU, и в RU при fallback
        let body = try JSONEncoder().encode(batch)

        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            performWithFallback(
                makeRequest: { base in
                    let url = base.appendingPathComponent("glass_game_ingest")
                    var req = URLRequest(url: url)
                    req.httpMethod = "POST"
                    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
                    req.httpBody = body
                    return req
                },
                completion: { result in
                    switch result {
                    case .success((_, let http, let data)):
                        // важно: performWithFallback может вернуть success с любым статусом
                        guard (200...299).contains(http.statusCode) else {
                            let bodyText = String(data: data, encoding: .utf8) ?? ""
                            cont.resume(throwing: NSError(
                                domain: "glass_game_ingest",
                                code: http.statusCode,
                                userInfo: ["body": bodyText]
                            ))
                            return
                        }
                        cont.resume()

                    case .failure(let err):
                        cont.resume(throwing: err)
                    }
                }
            )
        }
    }



    private func recordReportDelivery(sessionId: String, route: DeliveryRoute) {
        var s = loadDeliveryStats(sessionId: sessionId) ?? DeliveryStats()
        s.reportVia = route
        s.updatedAt = Date()
        saveDeliveryStats(sessionId: sessionId, s)
    }

    // MARK: - Pending finish store

    private func loadPendingFinishes() -> [PendingTripFinish] {
        guard let data = UserDefaults.standard.data(forKey: pendingFinishKey) else { return [] }
        return (try? JSONDecoder().decode([PendingTripFinish].self, from: data)) ?? []
    }

    private func savePendingFinishes(_ items: [PendingTripFinish]) {
        if let data = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(data, forKey: pendingFinishKey)
        }
    }

    private func upsertPendingFinish(_ pending: PendingTripFinish) {
        finishQueue.async { [weak self] in
            guard let self else { return }
            var items = self.loadPendingFinishes()
            items.removeAll { $0.session_id == pending.session_id }
            items.insert(pending, at: 0)
            self.savePendingFinishes(items)
        }
    }

    private func removePendingFinish(sessionId: String) {
        finishQueue.async { [weak self] in
            guard let self else { return }
            var items = self.loadPendingFinishes()
            let before = items.count
            items.removeAll { $0.session_id == sessionId }
            if items.count != before { self.savePendingFinishes(items) }
        }
    }

    // MARK: - Retry rules

    private func shouldRetry(statusCode: Int) -> Bool {
        statusCode == 408 || statusCode == 429 || (500...599).contains(statusCode)
    }

    private func scheduleRetry(
        attempt: Int,
        reason: String,
        cancelIf: (() -> Bool)? = nil,
        action: @escaping () -> Void
    ) {
        let exp = min(baseBackoff * pow(2.0, Double(attempt)), 15.0)
        let jitter = Double.random(in: 0...(0.25 * exp))
        let delay = exp + jitter

        logHandler?("[Network] retry in \(String(format: "%.2f", delay))s (attempt \(attempt + 1)) reason=\(reason)")

        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + delay) { [weak self] in
            _ = self // просто чтобы не ругался компилятор, если понадобится
            if let cancelIf, cancelIf() { return }
            action()
        }
    }


    // MARK: - Request helper

    private func makeRequest(url: URL, body: Data, bearerToken: String?) -> URLRequest {
        var r = URLRequest(url: url)
        r.httpMethod = "POST"
        r.timeoutInterval = 20
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Оставляем для совместимости (может быть полезно, даже если сервер перешёл на bearer)
        
        if let t = bearerToken, !t.isEmpty {
            r.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }

        r.httpBody = body
        return r
    }
    
    func publishIngestTotalsIfPresent(data: Data, fallbackSessionId: String) {
        guard !data.isEmpty else { return }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        let sessionId = (obj["session_id"] as? String) ?? fallbackSessionId

        func toInt(_ v: Any?) -> Int? {
            guard let v else { return nil }
            if let i = v as? Int { return i }
            if let n = v as? NSNumber { return n.intValue }
            if let d = v as? Double { return Int(d) }
            if let s = v as? String { return Int(s) }
            return nil
        }

        func toDouble(_ v: Any?) -> Double? {
            guard let v else { return nil }
            if let d = v as? Double { return d }
            if let n = v as? NSNumber { return n.doubleValue }
            if let i = v as? Int { return Double(i) }
            if let s = v as? String { return Double(s) }
            return nil
        }

        // V2 totals (optional: only post if at least one present)
        let payload: [String: Int?] = [
            "accel_sharp_total": toInt(obj["accel_sharp_total"]),
            "accel_emergency_total": toInt(obj["accel_emergency_total"]),

            "brake_sharp_total": toInt(obj["brake_sharp_total"]),
            "brake_emergency_total": toInt(obj["brake_emergency_total"]),

            "turn_sharp_total": toInt(obj["turn_sharp_total"]),
            "turn_emergency_total": toInt(obj["turn_emergency_total"]),

            "accel_in_turn_sharp_total": toInt(obj["accel_in_turn_sharp_total"]),
            "accel_in_turn_emergency_total": toInt(obj["accel_in_turn_emergency_total"]),

            "brake_in_turn_sharp_total": toInt(obj["brake_in_turn_sharp_total"]),
            "brake_in_turn_emergency_total": toInt(obj["brake_in_turn_emergency_total"]),

            "road_anomaly_low_total": toInt(obj["road_anomaly_low_total"]),
            "road_anomaly_high_total": toInt(obj["road_anomaly_high_total"]),
        ]

        // Live true penalty (server-side, per-batch)
        let livePenaltyTrue = toDouble(obj["live_penalty_true"])
        
        let liveExposureScoreTrue = toDouble(obj["live_exposure_score_true"])
        let liveExposurePresetTrue = obj["live_exposure_preset_true"] as? String

        // If server still returns legacy hard_* during rollout, you MAY keep this fallback.
        // If you want strict V2-only, delete this entire fallback block.
        let legacy: [String: Int?] = [
            "hard_accel_total": toInt(obj["hard_accel_total"]),
            "hard_brake_total": toInt(obj["hard_brake_total"]),
            "hard_turn_total": toInt(obj["hard_turn_total"]),
        ]

        // Decide if we have any V2 fields
        let hasAnyV2 = payload.values.contains { $0 != nil }
        let hasAnyLegacy = legacy.values.contains { $0 != nil }

        // Важно: теперь постим и когда есть только live_penalty_true (даже если totals не пришли)
        guard hasAnyV2 || hasAnyLegacy || (livePenaltyTrue != nil) else { return }

        var userInfo: [String: Any] = ["session_id": sessionId]

        if hasAnyV2 {
            for (k, v) in payload {
                if let v { userInfo[k] = v }
            }
        } else if hasAnyLegacy {
            // legacy fallback
            for (k, v) in legacy {
                if let v { userInfo[k] = v }
            }
        }

        if let livePenaltyTrue = toDouble(obj["live_penalty_true"]) {
            userInfo["live_penalty_true"] = livePenaltyTrue
        }
        
        if let liveExposureScoreTrue {
            userInfo["live_exposure_score_true"] = liveExposureScoreTrue
        }

        if let liveExposurePresetTrue {
            userInfo["live_exposure_preset_true"] = liveExposurePresetTrue
        }

        NotificationCenter.default.post(
            name: .networkManagerIngestTotalsDidUpdate,
            object: nil,
            userInfo: userInfo
        )
    }
    
    // MARK: - DriverId enforcement
    /// DriverId is mandatory. Network layer must not silently replace it with anon_*.
    private func resolvedDriverId(_ driverId: String) -> String {
        return driverId.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Driver auth (uniqueness + password)

    private struct DriverPrepareResp: Decodable { let status: String }

    private func postJSONWithFallback(
        path: String,
        bearer: String,
        body: [String: Any]
    ) async throws -> Data {
        let data = try JSONSerialization.data(withJSONObject: body, options: [])

        return try await withCheckedThrowingContinuation { cont in
            self.performWithFallback(
                makeRequest: { base in
                    let url = base.appendingPathComponent(path)
                    var req = URLRequest(url: url)
                    req.httpMethod = "POST"
                    req.timeoutInterval = 20
                    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
                    req.httpBody = data
                    return req
                },
                completion: { result in
                    switch result {
                    case .failure(let e):
                        cont.resume(throwing: e)
                    case .success((_, let http, let data)):
                        guard (200...299).contains(http.statusCode) else {
                            let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
                            cont.resume(throwing: NSError(domain: "HTTP", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: msg]))
                            return
                        }
                        cont.resume(returning: data)
                    }
                }
            )
        }
    }
    
    // MARK: - Account deletion

    func deleteAccount(
        deviceId: String,
        driverId: String
    ) async throws {
        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)

        let body: [String: Any] = [
            "driver_id": driverId
        ]

        _ = try await postJSONWithFallback(
            path: "account/delete",
            bearer: bearer,
            body: body
        )
    }

    /// Returns server status: known_device | need_password | new_driver
    func driverPrepare(deviceId: String, driverId: String) async throws -> String {
        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)
        let data = try await postJSONWithFallback(path: "driver/prepare", bearer: bearer, body: ["driver_id": driverId])
        let decoded = try JSONDecoder().decode(DriverPrepareResp.self, from: data)
        return decoded.status
    }

    func driverRegister(deviceId: String, driverId: String, password: String) async throws {
        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)
        _ = try await postJSONWithFallback(
            path: "driver/register",
            bearer: bearer,
            body: ["driver_id": driverId, "password": password]
        )
    }

    func driverLogin(deviceId: String, driverId: String, password: String) async throws {
        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)
        _ = try await postJSONWithFallback(
            path: "driver/login",
            bearer: bearer,
            body: ["driver_id": driverId, "password": password]
        )
    }
    
    // MARK: - App/device metadata
    private func appVersion() -> String? {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
    }

    private func appBuild() -> String? {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String
    }

    private func iosVersion() -> String? {
        UIDevice.current.systemVersion
    }

    private func deviceModelIdentifier() -> String? {
        var systemInfo = utsname()
        uname(&systemInfo)
        let mirror = Mirror(reflecting: systemInfo.machine)
        let id = mirror.children.reduce("") { acc, el in
            guard let v = el.value as? Int8, v != 0 else { return acc }
            return acc + String(UnicodeScalar(UInt8(v)))
        }
        return id.isEmpty ? nil : id
    }

    private func localeId() -> String? { Locale.current.identifier }
    private func timeZoneId() -> String? { TimeZone.current.identifier }
    
    private func pendingFinishSessionIdsSnapshot() -> Set<String> {
        Set(loadPendingFinishes().map { $0.session_id })
    }

    private func hasPendingFinish(sessionId: String) -> Bool {
        loadPendingFinishes().contains { $0.session_id == sessionId }
    }

    private func sortPendingQueueByPriorityLocked() {
        dispatchPrecondition(condition: .onQueue(sendQueue))

        let prioritySessions = pendingFinishSessionIdsSnapshot()

        pending.sort { lhs, rhs in
            let li = lhs.0
            let ri = rhs.0

            let lPriority = prioritySessions.contains(li.sessionId)
            let rPriority = prioritySessions.contains(ri.sessionId)

            if lPriority != rPriority {
                return lPriority && !rPriority
            }

            if lPriority && rPriority {
                if li.sessionId == ri.sessionId {
                    if li.batch.batch_seq != ri.batch.batch_seq {
                        return li.batch.batch_seq < ri.batch.batch_seq
                    }
                    return li.createdAt < ri.createdAt
                }

                return li.createdAt < ri.createdAt
            }

            return li.createdAt < ri.createdAt
        }
    }

    @discardableResult
    private func recordBatchDeliveryReturningStats(sessionId: String, route: DeliveryRoute) -> DeliveryStats {
        var s = loadDeliveryStats(sessionId: sessionId) ?? DeliveryStats()
        switch route {
        case .eu:
            s.euBatches += 1
        case .ru:
            s.ruBatches += 1
        }
        s.updatedAt = Date()
        lastDeliveryRoute = route
        saveDeliveryStats(sessionId: sessionId, s)
        return s
    }

    private func onBatchDelivered(sessionId: String, route: DeliveryRoute) {
        let before = loadDeliveryStats(sessionId: sessionId) ?? DeliveryStats()
        let after = recordBatchDeliveryReturningStats(sessionId: sessionId, route: route)

        guard hasPendingFinish(sessionId: sessionId) else { return }

        let beforeDelivered = before.euBatches + before.ruBatches
        let afterDelivered = after.euBatches + after.ruBatches

        if beforeDelivered == 0 && afterDelivered > 0 {
            logHandler?("[Recovery] first delivered batch for pending finish session=\(sessionId) route=\(route.rawValue) -> schedule immediate finish retry")
            scheduleRecovery(reason: "first delivered batch for pending finish session=\(sessionId)")
        } else {
            logHandler?("[Recovery] delivered batch for pending finish session=\(sessionId) route=\(route.rawValue)")
        }
    }




}
struct EmptyResponse: Decodable {}


// MARK: - Errors

struct httpStatus: Error, LocalizedError, CustomStringConvertible {
    let code: Int
    let body: String

    var description: String {
        "httpStatus(code: \(code), bodyPreview: \(body.prefix(300)))"
    }

    var errorDescription: String? {
        let preview = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if preview.isEmpty {
            return "HTTP \(code)"
        }
        return "HTTP \(code): \(preview.prefix(500))"
    }
}

enum NetworkError: Error, LocalizedError, CustomStringConvertible {
    case transport(error: Error)
    case invalidResponse

    var description: String {
        switch self {
        case .transport(let e): return "transport(\(e.localizedDescription))"
        case .invalidResponse: return "invalidResponse"
        }
    }

    var errorDescription: String? {
        switch self {
        case .transport(let e): return "Network transport error: \(e.localizedDescription)"
        case .invalidResponse: return "Invalid response from server"
        }
    }
}


// MARK: - Notifications

extension Notification.Name {
    static let networkManagerDeliveryRouteDidChange = Notification.Name("NetworkManagerDeliveryRouteDidChange")
}

extension Notification.Name {
    static let networkManagerIngestTotalsDidUpdate =
        Notification.Name("NetworkManagerIngestTotalsDidUpdate")
}

extension Notification.Name {
    static let networkManagerDeliveryStatsDidUpdate =
        Notification.Name("NetworkManagerDeliveryStatsDidUpdate")
}

