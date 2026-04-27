//
//  LocalizationKey.swift
//  TelemetryApp
//
//  Created by Alex on 15.03.26.
//

import Foundation

enum LocalizationKey: String {

    // Common
    case done
    case close
    case cancel
    case loading
    case settings
    case language
    case openSettings

    // Home
    case driverScore
    case recording
    case ready
    case start
    case stop
    case tripHistory
    case dashcam

    // Driver
    case driverLabel
    case enterDriverId
    case enterDriverIdButton
    case driverIdRequired
    
    case driverIdSection
    case current
    case changeDriverId
    
    case privacy
    case privacyDescription
    case deleteLocalData
    
    case delete    
    case deleteLocalDataTitle
    case deleteLocalDataMessage
    

    // Archive
    case tripArchiveTitle
    case tripDate
    case score
    case km
    case kmh
    case noTripsInArchive
    case archiveLoadError

    // Trip Report
    case tripDuration
    case averageSpeed
    case notEnoughData
    case excellent
    case verySmooth
    
    case backgroundLocation
    case backgroundLocationFooter
    case gpsDeniedTitle
    case gpsDeniedMessage
    case openIOSSettings
    case alwaysRequiredTitle
    case alwaysRequiredMessage
    case requestAlways
    case locationPermissionRequiredTitle
    case locationPermissionRequiredMessage
    case allowLocation
    
    case tripRecordingInProgress
    case readyToStartTrip
    
    case change
    case passwordConfirmationRequired
    case gps
    case unstableOperationTitle
    case alwaysPermissionHint
    case tripTime
    case distance
    case server
    case deviceShort
    case saveFishGame
    case monitoring
    case activity
    case state
    case tripsToday
    case dayMonitoringNote
    case viewTripHistory
    case offlineFinishMessage
    case searchingServerMessage
    case autoFinishSearchingMessage
    case autoFinished
    case tripFinishingGettingReport
    case driverAuthUnavailable
    case enableMonitoring
    case disableMonitoring
    case driver
    case driverIdSetupDescription
    case continueButton
    case password
    case create
    case signIn
    case back
    case checking
    case newDriverPasswordPrompt
    case existingDriverPasswordPrompt
    case serverStillRequiresPassword
    case driverIdNotCreatedOnServer

    case driverLoginMissing

    case report
    case reportLoading
    case reportLoadError
    
    case preliminaryReport
    case batchData
    case sent
    case notSent
    case total
    case serverProcessing
    case autoFinishLabel
    case drivingMode
    case drivingLoad
    case yourAverageRating
    case countedTripsYouHave
    case comparisonUnavailable
    case eventSummary
    case dangerousManeuvers
    case skidRisk
    case roadAnomalies
    case detailsV2
    case details
    case poor
    case normal
    case good
    case needsMoreCare
    case someSharpMoments
    case almostPerfect
    case outOf
    case driversGenitive
    case betterThanPrevTripsPrefix
    case betterThanPrevTripsSuffix
    case betterThanAllTripsPrefix
    case betterThanAllTripsSuffix
    case totalTripsCounted
    case tripReportTitle
    
    case yourTrip
    case totals
    case stops
    case comparison
    case startLabel
    case finishLabel
    case meters
    case vsPreviousTrip
    case vsAllTrips
    case previousTripsDriver
    case allTripsInDatabase
    case driverRank
    case totalDrivers
    case driverAverageScore
    case driverTripsCount
    
    case drivingModeMixed
    case drivingModeCity
    case drivingModeHighway
    case drivingModeUnknown
    
    case seconds
    
    case stopsCount
    case stopsTotal
    case stopsP95
    case stopsPerKm
    
    case eventClassesV2
    case eventClassSharpHelp
    case eventClassEmergencyHelp
    case eventClassAccelBrakeTurnHelp
    case eventClassAccelBrakeInTurnHelp
    case eventClassRoadAnomalyHelp
    case eventClassesImportantNote
    
    case glassGameRunning
    case glassGameOver
    case glassGameSpilled
    case glassGameBonus
    case glassGameHint
    case glassGameSpilledShort
    
    case dashcamTeaserMessage
    
    case currentSpeed
    
    case accelerationSharp
    case accelerationEmergency
    case brakingSharp
    case brakingEmergency
    case turnsSharp
    case turnsEmergency
    case accelerationInTurn
    case brakingInTurn
    case roadAnomaliesLow
    case roadAnomaliesHigh
    
    case yourDrivingLevelToday
    case betterThanDriversPercent
    case topDriversPercent
    
    case currentDrivingLevel
    case scoreDeltaLastTrips
    case ratingFormingTripsLeft
    case toNextLevelLeft
    case missingDeviceId
    case driverLevelRisky
    case driverLevelAverage
    case driverLevelSafe
    case driverLevelPro
    
    case lastFiveTripsTitle
    case excellentSeriesFiveOfFive
    case goodSeriesFourOfFive
    case decentSeriesThreeOfFive
    case tripCanBeImproved
    case keepGreenSeriesNextTrip
    case restoreGreenSeriesNextTrip
    
    case retry
    
    case privacyPolicy
    case termsOfUse

    case account
    case deleteAccount
    case deleteAccountTitle
    case deleteAccountMessage
    case unableToDeleteAccount
    case accountNotFound
    case errorGeneric
    case ok

    case changeDriverDuringTripTitle
    case changeDriverDuringTripMessage

    case onboardingTitle
    case onboardingAutoDetect
    case onboardingSummary
    case onboardingEvents
    case onboardingBackground
    case onboardingPermissionText
    
    case onboardingDashcamTitle
    case onboardingDashcamVideoRecording
    case onboardingDashcamForegroundOnly
    case onboardingDashcamNoHiddenBackground
    case onboardingDashcamFooter

    case onboardingCrashTitle
    case onboardingCrashProtectedClip
    case onboardingCrashBeforeAfter
    case onboardingCrashNotAutoDeleted
    case onboardingCrashFooter

    case onboardingPermissionsTitle
    case onboardingPermissionCamera
    case onboardingPermissionMicrophone
    case onboardingPermissionPhotoLibrary
    case onboardingPermissionsFooter

    case nextButton

    case crashDetected
    case crashImpactFormat
    
    case driverId
    case deviceConfirmationFailed
    case loginFailedTryAgain
    
    case driverIdNotFound
    case invalidPassword
    
    case deviceNotAuthorizedForDriver
    case archiveNotFound
    case archiveLoadFailedGeneric
    
    case driverScoreOneTrip
    
    
    case videoArchiveTitle
    case videoArchiveLockedMessage
    case crashRecordsSection
    case closeButton
    case deselectAll
    case selectAll
    case deleteSelectedNormalTitle
    case noButton
    case yesButton
    case recordsDeleteIrreversible
    case deleteCrashRecordsTitle
    case crashDeleteFirstConfirm
    case confirmDeleteCrashRecordsTitle
    case crashRecordsDeleteIrreversible
    case archiveUnavailableDuringRecordingTitle
    case stopVideoRecordingToOpenArchive
    case fileUnavailableTitle
    case unableToOpenRecordingMissingSegment
    case saveToLibraryTitle
    case saveToLibraryConfirmFormat
    case saveCompletedTitle
    case readyShort
    case totalRecordsFormat
    case usedStorageFormat
    case recordsSavedInTwoMinuteFragments
    case recordingMayContainSeveralFragments
    case reducesDeviceLoad
    case archiveEmpty
    case archiveEmptyDescription
    case summary
    case normalRecords
    case crashRecords
    case selected
    case all
    case crashFilter
    case normalFilter
    case segmentFormat
    case crashBadge
    case savedBadge
    case recordingFragmentFormat
    case durationLabelFormat
    case sizeGBFormat
    case saveToMediaLibrary
    case deleteNormal
    case deleteCrash
    case archiveFooterHint
    case videoRecordingInProgress
    case videoSavingInProgress
    case hideCamera
    case showCamera
    case stopVideo
    case smoothness
    case recordingTitleFormat
    case savedToLibraryFormat
    case crashTitleWithDateFormat
    
    
}
