package com.flockyou.data.model

/**
 * WiFi SSID pattern definitions for surveillance device detection.
 * Extracted from DetectionPatterns for maintainability.
 */
internal object SsidPatterns {
    val entries: List<DetectionPattern> = listOf(
        // Flock Safety - Primary patterns
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^flock[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 95,
            description = "Flock Safety ALPR Camera - captures license plates and vehicle characteristics",
            sourceUrl = "https://www.eff.org/deeplinks/2024/03/how-flock-safety-cameras-can-be-used-track-your-car"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^fs[_-].*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 90,
            description = "Flock Safety Camera (FS prefix variant)",
            sourceUrl = "https://www.flocksafety.com/products/flock-safety-cameras"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^falcon[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 90,
            description = "Flock Falcon ALPR - standard pole-mounted camera",
            sourceUrl = "https://www.flocksafety.com/products/falcon"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^sparrow[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 90,
            description = "Flock Sparrow ALPR - compact camera model",
            sourceUrl = "https://www.flocksafety.com/products/sparrow"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^condor[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 90,
            description = "Flock Condor ALPR - high-speed multi-lane camera",
            sourceUrl = "https://www.flocksafety.com/products/condor"
        ),
        
        // Penguin surveillance
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^penguin[_-]?.*",
            deviceType = DeviceType.PENGUIN_SURVEILLANCE,
            manufacturer = "Penguin",
            threatScore = 85,
            description = "Penguin Surveillance Device - mobile ALPR system"
        ),
        
        // Pigvision
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^pigvision[_-]?.*",
            deviceType = DeviceType.PIGVISION_SYSTEM,
            manufacturer = "Pigvision",
            threatScore = 85,
            description = "Pigvision Surveillance System"
        ),
        
        // Generic ALPR patterns
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^alpr[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = null,
            threatScore = 80,
            description = "Generic ALPR System - Automated License Plate Reader"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^lpr[_-]?cam.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = null,
            threatScore = 75,
            description = "License Plate Reader Camera"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^vigilant[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = "Motorola Solutions",
            threatScore = 85,
            description = "Vigilant ALPR (Motorola) - competitor to Flock"
        ),
        
        // ==================== Police Technology Patterns ====================
        
        // Motorola Solutions
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^moto[_-]?(body|cam|radio|apx).*",
            deviceType = DeviceType.MOTOROLA_POLICE_TECH,
            manufacturer = "Motorola Solutions",
            threatScore = 80,
            description = "Motorola police equipment (body camera, radio, APX)"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^apx[_-]?.*",
            deviceType = DeviceType.POLICE_RADIO,
            manufacturer = "Motorola Solutions",
            threatScore = 75,
            description = "Motorola APX Radio System"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^astro[_-]?.*",
            deviceType = DeviceType.POLICE_RADIO,
            manufacturer = "Motorola Solutions",
            threatScore = 70,
            description = "Motorola ASTRO Radio System"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^v[_-]?[35]00[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Motorola Solutions",
            threatScore = 80,
            description = "Motorola V300/V500 Body Camera"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^watchguard[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Motorola Solutions (WatchGuard)",
            threatScore = 80,
            description = "WatchGuard Body/Dash Camera System"
        ),
        
        // Axon (formerly TASER)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^axon[_-]?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 85,
            description = "Axon police equipment (body camera, TASER, etc.)",
            sourceUrl = "https://www.axon.com/products"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(axon[_-]?)?(body|flex)[_-]?[234]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Axon Enterprise",
            threatScore = 80,
            description = "Axon Body Camera (Body 2/3/4, Flex)",
            sourceUrl = "https://www.axon.com/products/body-cameras"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^taser[_-]?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 75,
            description = "TASER device with connectivity"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^evidence[_-]?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 70,
            description = "Axon Evidence.com sync device"
        ),
        
        // L3Harris
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^l3harris[_-]?.*",
            deviceType = DeviceType.L3HARRIS_SURVEILLANCE,
            manufacturer = "L3Harris Technologies",
            threatScore = 85,
            description = "L3Harris surveillance/communications equipment"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^stingray[_-]?.*",
            deviceType = DeviceType.STINGRAY_IMSI,
            manufacturer = "L3Harris Technologies",
            threatScore = 100,
            description = "StingRay Cell Site Simulator (IMSI Catcher)",
            sourceUrl = "https://www.eff.org/pages/cell-site-simulatorsimsi-catchers"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(hail|king|queen)storm[_-]?.*",
            deviceType = DeviceType.STINGRAY_IMSI,
            manufacturer = "L3Harris Technologies",
            threatScore = 100,
            description = "Hailstorm/Kingfish Cell Site Simulator",
            sourceUrl = "https://www.aclu.org/issues/privacy-technology/surveillance-technologies/stingray-tracking-devices"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(harris|xg)[_-]?[0-9]+.*",
            deviceType = DeviceType.POLICE_RADIO,
            manufacturer = "L3Harris Technologies",
            threatScore = 70,
            description = "Harris XG Radio System"
        ),
        
        // Digital Ally
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^digital[_-]?ally[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Digital Ally",
            threatScore = 75,
            description = "Digital Ally Body/Dash Camera"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^firstvu[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Digital Ally",
            threatScore = 75,
            description = "Digital Ally FirstVU Body Camera"
        ),
        
        // ==================== Mobile Forensics / Phone Extraction Devices ====================
        // CRITICAL: Detection of these devices near you may indicate device seizure risk

        // Cellebrite UFED (Universal Forensic Extraction Device)
        // $15,000-$30,000+ per unit, used by police, border agents, military
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^cellebrite[_-]?.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Cellebrite",
            threatScore = 95,
            description = "Cellebrite mobile forensics - can extract ALL data from phones including deleted content",
            sourceUrl = "https://www.eff.org/pages/cellebrite"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^ufed[_-]?(touch|4pc|ultimate|premium)?.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Cellebrite",
            threatScore = 95,
            description = "Cellebrite UFED - extracts messages, photos, app data, passwords from locked phones",
            sourceUrl = "https://cellebrite.com/en/ufed/"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(physical|logical)[_-]?analyzer.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Cellebrite",
            threatScore = 90,
            description = "Cellebrite Physical/Logical Analyzer - forensic data analysis tool"
        ),

        // GrayKey (Grayshift) - specifically designed to crack iPhones
        // $15,000-$30,000 per unit, law enforcement only
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^graykey[_-]?.*",
            deviceType = DeviceType.GRAYKEY_DEVICE,
            manufacturer = "Grayshift",
            threatScore = 95,
            description = "GrayKey iPhone forensics - can bypass iPhone passcodes and extract data",
            sourceUrl = "https://www.vice.com/en/article/graykey-iphone-unlocker-goes-on-sale-to-cops/"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^grayshift[_-]?.*",
            deviceType = DeviceType.GRAYKEY_DEVICE,
            manufacturer = "Grayshift",
            threatScore = 95,
            description = "Grayshift forensics device"
        ),

        // Magnet Forensics (cloud and device forensics)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^magnet[_-]?(forensic|axiom|acquire).*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Magnet Forensics",
            threatScore = 90,
            description = "Magnet Forensics - cloud and device data extraction"
        ),

        // MSAB XRY (Swedish mobile forensics)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(msab|xry)[_-]?.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "MSAB",
            threatScore = 90,
            description = "MSAB XRY mobile forensics system"
        ),

        // Oxygen Forensics
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^oxygen[_-]?forensic.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Oxygen Forensics",
            threatScore = 85,
            description = "Oxygen Forensic Detective - mobile data extraction"
        ),

        // Generic forensics patterns
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(mobile|phone|device)[_-]?forensic.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = null,
            threatScore = 85,
            description = "Mobile forensics device - may extract data from phones"
        ),
        
        // Genetec
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^genetec[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = "Genetec",
            threatScore = 80,
            description = "Genetec Security Center / AutoVu ALPR"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^autovu[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = "Genetec",
            threatScore = 85,
            description = "Genetec AutoVu ALPR System"
        ),
        
        // Getac (ruggedized police computers)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^getac[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = "Getac",
            threatScore = 60,
            description = "Getac ruggedized computer (often used in patrol vehicles)"
        ),
        
        // Panasonic Toughbook (common in police vehicles)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^toughbook[_-]?.*",
            deviceType = DeviceType.UNKNOWN_SURVEILLANCE,
            manufacturer = "Panasonic",
            threatScore = 55,
            description = "Panasonic Toughbook (commonly used by law enforcement)"
        ),

        // ==================== Smart Home / IoT Surveillance Patterns ====================

        // Ring Doorbells
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^ring[_-]?(doorbell|cam|setup|stick).*",
            deviceType = DeviceType.RING_DOORBELL,
            manufacturer = "Ring (Amazon)",
            threatScore = 40,
            description = "Ring doorbell/camera - shares footage with 2,500+ law enforcement agencies"
        ),

        // Nest/Google Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(nest|google)[_-]?(cam|doorbell|hello).*",
            deviceType = DeviceType.NEST_CAMERA,
            manufacturer = "Google/Nest",
            threatScore = 35,
            description = "Nest/Google camera - cloud-connected home surveillance"
        ),

        // Amazon Sidewalk
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(amazon[_-]?sidewalk|sidewalk[_-]?bridge).*",
            deviceType = DeviceType.AMAZON_SIDEWALK,
            manufacturer = "Amazon",
            threatScore = 45,
            description = "Amazon Sidewalk mesh network - shares bandwidth with neighbors/Amazon"
        ),

        // Wyze Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^wyze[_-]?(cam|doorbell|setup).*",
            deviceType = DeviceType.WYZE_CAMERA,
            manufacturer = "Wyze",
            threatScore = 35,
            description = "Wyze camera - budget smart home camera"
        ),

        // Arlo Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^arlo[_-]?(cam|pro|ultra|setup).*",
            deviceType = DeviceType.ARLO_CAMERA,
            manufacturer = "Arlo",
            threatScore = 35,
            description = "Arlo security camera"
        ),

        // Eufy/Anker Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^eufy[_-]?(cam|doorbell|security).*",
            deviceType = DeviceType.EUFY_CAMERA,
            manufacturer = "Eufy/Anker",
            threatScore = 35,
            description = "Eufy security camera"
        ),

        // Blink Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^blink[_-]?(cam|mini|setup).*",
            deviceType = DeviceType.BLINK_CAMERA,
            manufacturer = "Blink (Amazon)",
            threatScore = 40,
            description = "Blink camera (Amazon) - cloud-connected surveillance"
        ),

        // SimpliSafe
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^simplisafe[_-]?.*",
            deviceType = DeviceType.SIMPLISAFE_DEVICE,
            manufacturer = "SimpliSafe",
            threatScore = 35,
            description = "SimpliSafe security system"
        ),

        // ADT Security
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^adt[_-]?(pulse|cam|security).*",
            deviceType = DeviceType.ADT_DEVICE,
            manufacturer = "ADT",
            threatScore = 40,
            description = "ADT security system - may share with law enforcement"
        ),

        // Vivint
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^vivint[_-]?.*",
            deviceType = DeviceType.VIVINT_DEVICE,
            manufacturer = "Vivint",
            threatScore = 40,
            description = "Vivint smart home security"
        ),

        // ==================== Traffic Enforcement Patterns ====================

        // Speed Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(speed[_-]?cam|redflex|verra|xerox[_-]?ats).*",
            deviceType = DeviceType.SPEED_CAMERA,
            manufacturer = null,
            threatScore = 70,
            description = "Speed enforcement camera"
        ),

        // Red Light Cameras
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(red[_-]?light|intersection[_-]?cam|ats[_-]?).*",
            deviceType = DeviceType.RED_LIGHT_CAMERA,
            manufacturer = null,
            threatScore = 65,
            description = "Red light enforcement camera"
        ),

        // Toll Systems
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(ezpass|sunpass|fastrak|toll[_-]?gantry).*",
            deviceType = DeviceType.TOLL_READER,
            manufacturer = null,
            threatScore = 50,
            description = "Electronic toll collection system"
        ),

        // ==================== Network Attack/Pentest Device Patterns ====================

        // WiFi Pineapple
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(pineapple|hak5|wifi[_-]?pineapple).*",
            deviceType = DeviceType.WIFI_PINEAPPLE,
            manufacturer = "Hak5",
            threatScore = 90,
            description = "WiFi Pineapple - network auditing/attack tool"
        ),

        // ==================== Retail/Commercial WiFi Tracking Patterns ====================
        // These systems track customers via WiFi probe requests and MAC addresses

        // Major retail analytics providers
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(retailnext|shoppertrak|footfall).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = null,
            threatScore = 50,
            description = "Retail foot traffic analytics - tracks customer movement via WiFi"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^euclid[_-]?(analytics|element).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = "Euclid Analytics",
            threatScore = 55,
            description = "Euclid Analytics - retail WiFi tracking and analytics"
        ),

        // Cisco Meraki WiFi analytics (very common in retail/enterprise)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^meraki[_-]?(analytics|presence|scanning).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = "Cisco Meraki",
            threatScore = 45,
            description = "Cisco Meraki WiFi analytics - location and presence tracking"
        ),

        // Aruba/HPE (common in enterprise, can track devices)
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^aruba[_-]?(analytics|meridian|location).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = "Aruba (HPE)",
            threatScore = 45,
            description = "Aruba WiFi analytics - enterprise location tracking"
        ),

        // Mist Systems (Juniper) - AI-driven analytics
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^mist[_-]?(ai|analytics).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = "Mist (Juniper)",
            threatScore = 45,
            description = "Mist AI analytics - machine learning WiFi tracking"
        ),

        // Generic WiFi analytics patterns
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(wifi|wlan)[_-]?(analytics|tracking|presence).*",
            deviceType = DeviceType.CROWD_ANALYTICS,
            manufacturer = null,
            threatScore = 50,
            description = "WiFi analytics system - may track device presence and movement"
        ),

        // ==================== Hidden Camera WiFi Patterns ====================
        // Common SSIDs from cheap IP cameras often used for covert surveillance

        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(ipc|ipcam|ip[_-]?cam(era)?)[_-]?[0-9a-f]*$",
            deviceType = DeviceType.HIDDEN_CAMERA,
            manufacturer = null,
            threatScore = 70,
            description = "IP Camera default SSID - common in hidden cameras"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(wifi[_-]?cam|wificam)[_-]?[0-9a-f]*$",
            deviceType = DeviceType.HIDDEN_CAMERA,
            manufacturer = null,
            threatScore = 70,
            description = "WiFi Camera default SSID"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^p2p[_-]?[0-9a-f]+$",
            deviceType = DeviceType.HIDDEN_CAMERA,
            manufacturer = null,
            threatScore = 65,
            description = "P2P Camera protocol SSID"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(spy|nanny|hidden|covert|pinhole)[_-]?cam.*",
            deviceType = DeviceType.HIDDEN_CAMERA,
            manufacturer = null,
            threatScore = 85,
            description = "Explicitly named hidden/spy camera"
        ),
        DetectionPattern(
            type = PatternType.SSID_REGEX,
            pattern = "(?i)^(clock|smoke|outlet|charger|usb)[_-]?cam.*",
            deviceType = DeviceType.HIDDEN_CAMERA,
            manufacturer = null,
            threatScore = 80,
            description = "Disguised camera (clock, smoke detector, USB charger, etc.)"
        )
    )
}
