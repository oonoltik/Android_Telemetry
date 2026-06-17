//
//  GoldenMotionProjection.swift
//  TelemetryApp
//
//  Created by Alex on 08.05.26.
//

import Foundation
import simd

struct GoldenMotionProjectionInput {
    let accelX: Double
    let accelY: Double
    let accelZ: Double
    let courseRad: Double?
}

struct GoldenMotionProjectionOutput {
    let aLongG: Double?
    let aLatG: Double?
    let aVertG: Double
}

enum GoldenMotionProjection {
    static func compute(input: GoldenMotionProjectionInput) -> GoldenMotionProjectionOutput {
        let aRef = SIMD3<Double>(
            input.accelX,
            input.accelY,
            input.accelZ
        )

        let aVert = aRef.z
        let aH = SIMD2<Double>(aRef.x, aRef.y)

        guard let cr = input.courseRad else {
            return GoldenMotionProjectionOutput(
                aLongG: nil,
                aLatG: nil,
                aVertG: aVert
            )
        }

        let vHat = SIMD2<Double>(cos(cr), sin(cr))
        let vPerp = SIMD2<Double>(-sin(cr), cos(cr))

        let aLong = Double(aH.x * vHat.x + aH.y * vHat.y)
        let aLat = Double(aH.x * vPerp.x + aH.y * vPerp.y)

        return GoldenMotionProjectionOutput(
            aLongG: aLong,
            aLatG: aLat,
            aVertG: aVert
        )
    }
}
