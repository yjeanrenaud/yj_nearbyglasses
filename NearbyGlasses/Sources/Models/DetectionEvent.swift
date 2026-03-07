import Foundation

struct DetectionEvent: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let deviceAddress: String
    let deviceName: String?
    let rssi: Int
    let companyId: String?
    let companyName: String
    let manufacturerData: String?
    let detectionReason: String

    init(
        timestamp: Date = Date(),
        deviceAddress: String,
        deviceName: String?,
        rssi: Int,
        companyId: String?,
        companyName: String,
        manufacturerData: String?,
        detectionReason: String
    ) {
        self.id = UUID()
        self.timestamp = timestamp
        self.deviceAddress = deviceAddress
        self.deviceName = deviceName
        self.rssi = rssi
        self.companyId = companyId
        self.companyName = companyName
        self.manufacturerData = manufacturerData
        self.detectionReason = detectionReason
    }

    // MARK: - Known Company IDs
    static let metaCompanyId1: UInt16 = 0x01AB
    static let metaCompanyId2: UInt16 = 0x058E
    static let essilorCompanyId: UInt16 = 0x0D53
    static let snapCompanyId: UInt16 = 0x03C2

    static func isSmartGlasses(companyId: UInt16?, deviceName: String?) -> (Bool, String) {
        var reasons: [String] = []

        if let cid = companyId {
            if cid == metaCompanyId1 {
                reasons.append(String(format: NSLocalizedString("reason_meta_company_id", comment: ""), "0x01AB"))
            }
            if cid == metaCompanyId2 {
                reasons.append(String(format: NSLocalizedString("reason_meta_company_id", comment: ""), "0x058E"))
            }
            if cid == essilorCompanyId {
                reasons.append(String(format: NSLocalizedString("reason_essilor_company_id", comment: ""), "0x0D53"))
            }
            if cid == snapCompanyId {
                reasons.append(String(format: NSLocalizedString("reason_snap_company_id", comment: ""), "0x03C2"))
            }
        }

        if let name = deviceName {
            let nameLower = name.lowercased()
            if nameLower.contains("rayban") {
                reasons.append(String(format: NSLocalizedString("reason_name_contains", comment: ""), "rayban"))
            } else if nameLower.contains("ray-ban") {
                reasons.append(String(format: NSLocalizedString("reason_name_contains", comment: ""), "ray-ban"))
            } else if nameLower.contains("ray ban") {
                reasons.append(String(format: NSLocalizedString("reason_name_contains", comment: ""), "ray ban"))
            }
        }

        return (!reasons.isEmpty, reasons.joined(separator: ", "))
    }

    static func getCompanyName(_ companyId: UInt16) -> String {
        switch companyId {
        case metaCompanyId1, metaCompanyId2:
            return NSLocalizedString("company_meta", comment: "")
        case essilorCompanyId:
            return NSLocalizedString("company_essilor", comment: "")
        case snapCompanyId:
            return NSLocalizedString("company_snap", comment: "")
        default:
            return String(format: NSLocalizedString("company_unknown", comment: ""), String(format: "0x%04X", companyId))
        }
    }

    func toLogString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        let time = formatter.string(from: timestamp)
        let name = deviceName ?? NSLocalizedString("unknown_device", comment: "")
        return String(format: NSLocalizedString("log_detection_line", comment: ""), time, name, rssi, detectionReason)
    }

    func toJson() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        let ts = Int(timestamp.timeIntervalSince1970 * 1000)
        let nameJson = deviceName.map { "\"\($0)\"" } ?? "null"
        let cidJson = companyId.map { "\"\($0)\"" } ?? "null"
        let mfgJson = manufacturerData.map { "\"\($0)\"" } ?? "null"
        return """
        {
            "timestamp": \(ts),
            "timestampFormatted": "\(formatter.string(from: timestamp))",
            "deviceAddress": "\(deviceAddress)",
            "deviceName": \(nameJson),
            "rssi": \(rssi),
            "companyId": \(cidJson),
            "companyName": "\(companyName)",
            "manufacturerData": \(mfgJson),
            "detectionReason": "\(detectionReason)"
        }
        """
    }
}
