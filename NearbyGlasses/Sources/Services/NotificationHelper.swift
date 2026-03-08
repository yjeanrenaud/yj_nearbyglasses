import Foundation
import UserNotifications
import os.log

final class NotificationHelper: Sendable {
    static let shared = NotificationHelper()

    private let logger = Logger(subsystem: "ch.pocketpc.nearbyglasses", category: "NotificationHelper")

    private enum CategoryId {
        static let detection = "GLASSES_DETECTION"
    }

    func requestAuthorization(completion: @escaping @Sendable (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                self.logger.error("Notification authorization error: \(error.localizedDescription)")
            }
            DispatchQueue.main.async {
                completion(granted)
            }
        }
    }

    func showDetectionNotification(event: DetectionEvent) {
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("notifyText", comment: "")
        let deviceName = event.deviceName ?? NSLocalizedString("notification_unknown_device", comment: "")
        content.body = String(format: NSLocalizedString("notification_detected_text", comment: ""), deviceName, event.rssi)
        content.sound = .default
        content.categoryIdentifier = CategoryId.detection

        // Add detail info in userInfo for potential rich notification
        content.userInfo = [
            "deviceName": deviceName,
            "rssi": event.rssi,
            "reason": event.detectionReason,
            "company": event.companyName
        ]

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil // Deliver immediately
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                self.logger.error("Failed to show notification: \(error.localizedDescription)")
            }
        }
    }
}
