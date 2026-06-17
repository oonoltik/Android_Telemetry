import Foundation

struct GoldenImuInput: Codable {
    let t: String
    let speed_m_s: Double
    let accel_x: Double
    let accel_y: Double
    let accel_z: Double
    let gyro_x: Double
    let gyro_y: Double
    let gyro_z: Double
    let yaw: Double
    let pitch: Double
    let roll: Double
    let course_rad: Double?
}

struct GoldenMotionOutput: Codable {
    let t: String
    let a_long_g: Double?
    let a_lat_g: Double?
    let a_vert_g: Double?
}

enum GoldenMotionExporter {
    static func export(inputs: [GoldenImuInput]) -> [GoldenMotionOutput] {
        return inputs.map { input in
            let projection = GoldenMotionProjection.compute(
                input: GoldenMotionProjectionInput(
                    accelX: input.accel_x,
                    accelY: input.accel_y,
                    accelZ: input.accel_z,
                    courseRad: input.course_rad ?? 0.0
                )
            )

            return GoldenMotionOutput(
                t: input.t,
                a_long_g: projection.aLongG,
                a_lat_g: projection.aLatG,
                a_vert_g: projection.aVertG
            )
        }
    }

    static func exportJson(from inputJson: String) throws -> String {
        let data = Data(inputJson.utf8)
        let inputs = try JSONDecoder().decode([GoldenImuInput].self, from: data)
        let outputs = export(inputs: inputs)

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let outputData = try encoder.encode(outputs)
        return String(data: outputData, encoding: .utf8) ?? "[]"
    }
}
