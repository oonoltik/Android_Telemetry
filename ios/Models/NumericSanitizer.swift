import Foundation

enum NumericSanitizer {
    static func raw(_ x: Double) -> Double {
        guard x.isFinite else { return 0.0 }
        return x
    }

    static func rawOptional(_ x: Double?) -> Double? {
        guard let x else { return nil }
        guard x.isFinite else { return nil }
        return x
    }

    static func metric(_ x: Double, digits: Int = 6) -> Double {
        guard x.isFinite else { return 0.0 }
        let scale = pow(10.0, Double(digits))
        return (x * scale).rounded() / scale
    }

    static func metricOptional(_ x: Double?, digits: Int = 6) -> Double? {
        guard let x else { return nil }
        guard x.isFinite else { return nil }
        let scale = pow(10.0, Double(digits))
        return (x * scale).rounded() / scale
    }
}
