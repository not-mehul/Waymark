package com.waymark.data.catalog

/**
 * IATA airline designators and the names travelers know the airlines by.
 *
 * Hand-compiled for the carriers people actually book — full-service, low-cost
 * and the larger regionals — rather than generated from one of the public
 * dumps, which still carry defunct airlines on codes that have since been
 * reassigned. A designator that is not here prints as itself, which is always
 * correct if not always friendly.
 */
object Airlines {

    private val names: Map<String, String> = mapOf(
        // North America
        "AA" to "American Airlines", "AC" to "Air Canada", "AS" to "Alaska Airlines",
        "B6" to "JetBlue", "DL" to "Delta Air Lines", "F9" to "Frontier Airlines",
        "G4" to "Allegiant Air", "HA" to "Hawaiian Airlines", "MX" to "Breeze Airways",
        "NK" to "Spirit Airlines", "PD" to "Porter Airlines", "QX" to "Horizon Air",
        "SY" to "Sun Country Airlines", "TS" to "Air Transat", "UA" to "United Airlines",
        "WN" to "Southwest Airlines", "WS" to "WestJet", "OO" to "SkyWest Airlines",
        "YX" to "Republic Airways", "9E" to "Endeavor Air", "MQ" to "Envoy Air",
        "OH" to "PSA Airlines", "F8" to "Flair Airlines",
        "4N" to "Air North",

        // Latin America and the Caribbean
        "AM" to "Aeroméxico", "AR" to "Aerolíneas Argentinas", "AV" to "Avianca",
        "BW" to "Caribbean Airlines", "CM" to "Copa Airlines", "G3" to "Gol",
        "AD" to "Azul", "LA" to "LATAM Airlines", "JJ" to "LATAM Brasil",
        "H2" to "Sky Airline", "JA" to "JetSMART", "Y4" to "Volaris",
        "VB" to "Viva Aerobus", "UP" to "Bahamasair",
        "OB" to "Boliviana de Aviación",
        "XL" to "LATAM Ecuador", "P5" to "Wingo",

        // Europe
        "A3" to "Aegean Airlines", "AF" to "Air France", "AY" to "Finnair",
        "AZ" to "ITA Airways", "BA" to "British Airways", "BT" to "airBaltic",
        "DY" to "Norwegian", "D8" to "Norwegian", "EI" to "Aer Lingus",
        "EN" to "Air Dolomiti", "EW" to "Eurowings", "FI" to "Icelandair",
        "FR" to "Ryanair", "IB" to "Iberia", "I2" to "Iberia Express",
        "JU" to "Air Serbia", "KL" to "KLM", "LG" to "Luxair", "LH" to "Lufthansa",
        "LO" to "LOT Polish Airlines", "LS" to "Jet2", "LX" to "Swiss",
        "OS" to "Austrian Airlines", "OU" to "Croatia Airlines", "PC" to "Pegasus Airlines",
        "RO" to "TAROM", "SK" to "SAS", "SN" to "Brussels Airlines",
        "TK" to "Turkish Airlines", "TO" to "Transavia France", "HV" to "Transavia",
        "TP" to "TAP Air Portugal", "U2" to "easyJet", "EC" to "easyJet Europe",
        "DS" to "easyJet Switzerland", "UX" to "Air Europa", "V7" to "Volotea",
        "VS" to "Virgin Atlantic", "VY" to "Vueling", "W6" to "Wizz Air",
        "W4" to "Wizz Air Malta", "W9" to "Wizz Air UK", "XQ" to "SunExpress",
        "BY" to "TUI Airways", "X3" to "TUI fly", "OA" to "Olympic Air",
        "WF" to "Widerøe", "LM" to "Loganair", "EZ" to "Sun-Air",
        "DE" to "Condor", "SM" to "Air Cairo",
        "FB" to "Bulgaria Air", "KM" to "KM Malta Airlines", "NT" to "Binter Canarias",
        "YW" to "Air Nostrum",
        "J2" to "Azerbaijan Airlines", "A9" to "Georgian Airways", "QS" to "Smartwings",
        "SU" to "Aeroflot", "S7" to "S7 Airlines",
        "HY" to "Uzbekistan Airways", "KC" to "Air Astana", "FZ" to "flydubai",

        // Middle East and Africa
        "EK" to "Emirates", "EY" to "Etihad Airways", "QR" to "Qatar Airways",
        "GF" to "Gulf Air", "WY" to "Oman Air", "SV" to "Saudia", "XY" to "flynas",
        "F3" to "flyadeal", "RJ" to "Royal Jordanian", "ME" to "Middle East Airlines",
        "LY" to "El Al", "KU" to "Kuwait Airways", "G9" to "Air Arabia",
        "IR" to "Iran Air", "IA" to "Iraqi Airways", "J9" to "Jazeera Airways",
        "MS" to "EgyptAir", "AT" to "Royal Air Maroc", "ET" to "Ethiopian Airlines",
        "KQ" to "Kenya Airways", "SA" to "South African Airways", "FA" to "FlySafair",
        "4Z" to "Airlink", "TU" to "Tunisair", "AH" to "Air Algérie",
        "WB" to "RwandAir", "TC" to "Air Tanzania", "UR" to "Uganda Airlines",
        "P4" to "Air Peace", "HF" to "Air Côte d'Ivoire", "DT" to "TAAG Angola Airlines",
        "MK" to "Air Mauritius", "HM" to "Air Seychelles", "UU" to "Air Austral",
        "TM" to "LAM Mozambique Airlines", "BP" to "Air Botswana",
        "W3" to "Arik Air", "KP" to "ASKY Airlines", "8U" to "Afriqiyah Airways",

        // South Asia
        "AI" to "Air India", "IX" to "Air India Express", "6E" to "IndiGo",
        "SG" to "SpiceJet", "QP" to "Akasa Air",
        "UL" to "SriLankan Airlines", "PK" to "Pakistan International Airlines",
        "BG" to "Biman Bangladesh Airlines", "BS" to "US-Bangla Airlines",
        "KB" to "Druk Air", "RA" to "Nepal Airlines", "Q2" to "Maldivian",

        // East and Southeast Asia
        "CA" to "Air China", "CZ" to "China Southern Airlines", "MU" to "China Eastern Airlines",
        "HU" to "Hainan Airlines", "3U" to "Sichuan Airlines", "MF" to "Xiamen Airlines",
        "ZH" to "Shenzhen Airlines", "FM" to "Shanghai Airlines", "9C" to "Spring Airlines",
        "HO" to "Juneyao Air", "SC" to "Shandong Airlines", "CX" to "Cathay Pacific",
        "UO" to "HK Express", "HX" to "Hong Kong Airlines", "NX" to "Air Macau",
        "BR" to "EVA Air", "CI" to "China Airlines", "JX" to "Starlux Airlines",
        "IT" to "Tigerair Taiwan", "B7" to "Uni Air", "JL" to "Japan Airlines",
        "NH" to "ANA", "MM" to "Peach", "GK" to "Jetstar Japan", "BC" to "Skymark Airlines",
        "NU" to "Japan Transocean Air", "HD" to "Air Do", "KE" to "Korean Air",
        "OZ" to "Asiana Airlines", "7C" to "Jeju Air", "LJ" to "Jin Air",
        "TW" to "T'way Air", "BX" to "Air Busan", "ZE" to "Eastar Jet",
        "RS" to "Air Seoul", "OM" to "MIAT Mongolian Airlines", "SQ" to "Singapore Airlines",
        "TR" to "Scoot", "MH" to "Malaysia Airlines", "AK" to "AirAsia",
        "D7" to "AirAsia X", "FD" to "Thai AirAsia", "QZ" to "Indonesia AirAsia",
        "Z2" to "Philippines AirAsia", "OD" to "Batik Air Malaysia", "TG" to "Thai Airways",
        "PG" to "Bangkok Airways", "SL" to "Thai Lion Air",
        "DD" to "Nok Air", "VN" to "Vietnam Airlines", "VJ" to "VietJet Air",
        "QH" to "Bamboo Airways", "PR" to "Philippine Airlines",
        "5J" to "Cebu Pacific", "GA" to "Garuda Indonesia", "JT" to "Lion Air",
        "ID" to "Batik Air", "IU" to "Super Air Jet", "QG" to "Citilink",
        "BI" to "Royal Brunei Airlines", "K6" to "Cambodia Angkor Air",
        "QV" to "Lao Airlines", "8M" to "Myanmar Airways International",

        // Oceania
        "QF" to "Qantas", "JQ" to "Jetstar", "VA" to "Virgin Australia",
        "ZL" to "Rex Airlines", "NZ" to "Air New Zealand", "FJ" to "Fiji Airways",
        "PX" to "Air Niugini", "TN" to "Air Tahiti Nui", "VT" to "Air Tahiti",
        "SB" to "Aircalin", "IE" to "Solomon Airlines",
        "OL" to "Samoa Airways", "QQ" to "Alliance Airlines",
    )

    /** "BA" → "British Airways"; an unknown designator comes back as itself. */
    fun name(code: String): String {
        val key = code.trim().uppercase()
        return names[key] ?: key
    }

    fun knows(code: String): Boolean = code.trim().uppercase() in names

    val size: Int get() = names.size
}
