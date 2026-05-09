package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportArchivePollingContractTest {

    @Test
    fun report_polling_continues_until_terminal_ready_state() {
        val api = FakeReportArchiveApi(
            reportResponses = ArrayDeque(
                listOf(
                    ReportPollResponse.Pending(nextPollToken = "poll-2"),
                    ReportPollResponse.Processing(nextPollToken = "poll-3"),
                    ReportPollResponse.Ready(reportId = "report-1"),
                )
            )
        )
        val sink = FakeReportArchiveUiSink()

        val poller = FakeReportArchivePoller(
            api = api,
            sink = sink,
        )

        poller.pollReport(
            request = ReportPollRequest(
                tripId = "trip-1",
                pollToken = "poll-1",
            )
        )

        assertEquals(
            listOf(
                ReportPollRequest(tripId = "trip-1", pollToken = "poll-1"),
                ReportPollRequest(tripId = "trip-1", pollToken = "poll-2"),
                ReportPollRequest(tripId = "trip-1", pollToken = "poll-3"),
            ),
            api.reportPollRequests,
        )
        assertEquals(
            listOf(
                ReportArchiveUiState.ReportPending,
                ReportArchiveUiState.ReportProcessing,
                ReportArchiveUiState.ReportReady,
            ),
            sink.states,
        )
    }

    @Test
    fun report_polling_stops_on_failed_state_and_exposes_missing_batches() {
        val missing = listOf(
            MissingBatch(
                sessionId = "session-1",
                batchSeq = 3,
            ),
            MissingBatch(
                sessionId = "session-1",
                batchSeq = 4,
            ),
        )

        val api = FakeReportArchiveApi(
            reportResponses = ArrayDeque(
                listOf(
                    ReportPollResponse.Processing(nextPollToken = "poll-2"),
                    ReportPollResponse.Failed(
                        reason = "missing_batches",
                        missingBatches = missing,
                    ),
                )
            )
        )
        val sink = FakeReportArchiveUiSink()

        val poller = FakeReportArchivePoller(
            api = api,
            sink = sink,
        )

        poller.pollReport(
            request = ReportPollRequest(
                tripId = "trip-1",
                pollToken = "poll-1",
            )
        )

        assertEquals(
            listOf(
                ReportPollRequest(tripId = "trip-1", pollToken = "poll-1"),
                ReportPollRequest(tripId = "trip-1", pollToken = "poll-2"),
            ),
            api.reportPollRequests,
        )
        assertEquals(
            listOf(
                ReportArchiveUiState.ReportProcessing,
                ReportArchiveUiState.ReportFailed,
            ),
            sink.states,
        )
        assertEquals(missing, sink.visibleMissingBatches)
    }

    @Test
    fun archive_query_uses_canonical_driver_time_range_and_pagination_contract() {
        val api = FakeReportArchiveApi(
            archiveResponses = ArrayDeque(
                listOf(
                    ArchiveQueryResponse.Page(
                        items = listOf("archive-trip-1"),
                        nextPageToken = "page-2",
                    ),
                    ArchiveQueryResponse.Page(
                        items = listOf("archive-trip-2"),
                        nextPageToken = null,
                    ),
                )
            )
        )

        val archive = FakeTripArchiveRepository(api)

        val result = archive.queryArchive(
            request = ArchiveQueryRequest(
                driverId = "driver-1",
                fromIsoUtc = "2025-01-01T00:00:00Z",
                toIsoUtc = "2025-01-31T23:59:59Z",
                pageToken = null,
                limit = 50,
            )
        )

        assertEquals(
            listOf(
                ArchiveQueryRequest(
                    driverId = "driver-1",
                    fromIsoUtc = "2025-01-01T00:00:00Z",
                    toIsoUtc = "2025-01-31T23:59:59Z",
                    pageToken = null,
                    limit = 50,
                ),
                ArchiveQueryRequest(
                    driverId = "driver-1",
                    fromIsoUtc = "2025-01-01T00:00:00Z",
                    toIsoUtc = "2025-01-31T23:59:59Z",
                    pageToken = "page-2",
                    limit = 50,
                ),
            ),
            api.archiveQueryRequests,
        )
        assertEquals(
            listOf("archive-trip-1", "archive-trip-2"),
            result.items,
        )
        assertEquals(null, result.nextPageToken)
    }

    @Test
    fun archive_query_empty_result_is_visible_as_empty_state_not_error() {
        val api = FakeReportArchiveApi(
            archiveResponses = ArrayDeque(
                listOf(
                    ArchiveQueryResponse.Page(
                        items = emptyList(),
                        nextPageToken = null,
                    )
                )
            )
        )

        val archive = FakeTripArchiveRepository(api)

        val result = archive.queryArchive(
            request = ArchiveQueryRequest(
                driverId = "driver-1",
                fromIsoUtc = "2025-02-01T00:00:00Z",
                toIsoUtc = "2025-02-28T23:59:59Z",
                pageToken = null,
                limit = 50,
            )
        )

        assertTrue(result.items.isEmpty())
        assertEquals(null, result.nextPageToken)
        assertEquals(ArchiveQueryVisibility.Empty, result.visibility)
    }
}

private data class ReportPollRequest(
    val tripId: String,
    val pollToken: String,
)

private data class MissingBatch(
    val sessionId: String,
    val batchSeq: Int,
)

private sealed class ReportPollResponse {
    data class Pending(
        val nextPollToken: String,
    ) : ReportPollResponse()

    data class Processing(
        val nextPollToken: String,
    ) : ReportPollResponse()

    data class Ready(
        val reportId: String,
    ) : ReportPollResponse()

    data class Failed(
        val reason: String,
        val missingBatches: List<MissingBatch>,
    ) : ReportPollResponse()
}

private enum class ReportArchiveUiState {
    ReportPending,
    ReportProcessing,
    ReportReady,
    ReportFailed,
}

private data class ArchiveQueryRequest(
    val driverId: String,
    val fromIsoUtc: String,
    val toIsoUtc: String,
    val pageToken: String?,
    val limit: Int,
)

private sealed class ArchiveQueryResponse {
    data class Page(
        val items: List<String>,
        val nextPageToken: String?,
    ) : ArchiveQueryResponse()
}

private enum class ArchiveQueryVisibility {
    Items,
    Empty,
}

private data class ArchiveQueryResult(
    val items: List<String>,
    val nextPageToken: String?,
    val visibility: ArchiveQueryVisibility,
)

private class FakeReportArchiveApi(
    private val reportResponses: ArrayDeque<ReportPollResponse> = ArrayDeque(),
    private val archiveResponses: ArrayDeque<ArchiveQueryResponse> = ArrayDeque(),
) {
    val reportPollRequests = mutableListOf<ReportPollRequest>()
    val archiveQueryRequests = mutableListOf<ArchiveQueryRequest>()

    fun pollReport(request: ReportPollRequest): ReportPollResponse {
        reportPollRequests += request

        return reportResponses.removeFirst()
    }

    fun queryArchive(request: ArchiveQueryRequest): ArchiveQueryResponse {
        archiveQueryRequests += request

        return archiveResponses.removeFirst()
    }
}

private class FakeReportArchiveUiSink {
    val states = mutableListOf<ReportArchiveUiState>()
    val visibleMissingBatches = mutableListOf<MissingBatch>()

    fun emit(state: ReportArchiveUiState) {
        states += state
    }

    fun showMissingBatches(missingBatches: List<MissingBatch>) {
        visibleMissingBatches += missingBatches
    }
}

private class FakeReportArchivePoller(
    private val api: FakeReportArchiveApi,
    private val sink: FakeReportArchiveUiSink,
) {
    fun pollReport(request: ReportPollRequest) {
        var currentRequest = request

        while (true) {
            when (val response = api.pollReport(currentRequest)) {
                is ReportPollResponse.Pending -> {
                    sink.emit(ReportArchiveUiState.ReportPending)
                    currentRequest = currentRequest.copy(
                        pollToken = response.nextPollToken,
                    )
                }

                is ReportPollResponse.Processing -> {
                    sink.emit(ReportArchiveUiState.ReportProcessing)
                    currentRequest = currentRequest.copy(
                        pollToken = response.nextPollToken,
                    )
                }

                is ReportPollResponse.Ready -> {
                    sink.emit(ReportArchiveUiState.ReportReady)
                    return
                }

                is ReportPollResponse.Failed -> {
                    sink.emit(ReportArchiveUiState.ReportFailed)
                    sink.showMissingBatches(response.missingBatches)
                    return
                }
            }
        }
    }
}

private class FakeTripArchiveRepository(
    private val api: FakeReportArchiveApi,
) {
    fun queryArchive(request: ArchiveQueryRequest): ArchiveQueryResult {
        val items = mutableListOf<String>()
        var currentRequest = request
        var nextPageToken: String?

        do {
            val response = api.queryArchive(currentRequest)

            when (response) {
                is ArchiveQueryResponse.Page -> {
                    items += response.items
                    nextPageToken = response.nextPageToken

                    if (nextPageToken != null) {
                        currentRequest = currentRequest.copy(
                            pageToken = nextPageToken,
                        )
                    }
                }
            }
        } while (nextPageToken != null)

        return ArchiveQueryResult(
            items = items,
            nextPageToken = null,
            visibility = if (items.isEmpty()) {
                ArchiveQueryVisibility.Empty
            } else {
                ArchiveQueryVisibility.Items
            },
        )
    }
}