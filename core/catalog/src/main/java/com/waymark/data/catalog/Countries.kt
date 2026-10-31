package com.waymark.data.catalog

import com.waymark.domain.model.Place

/**
 * Every country, and the four things a traveler wants in the twenty minutes
 * between landing and finding the train.
 *
 * This replaces a hand-written table of nine cities. Nine cities is a demo:
 * anyone flying to the tenth got a blank screen, and the entries that did
 * exist carried a mix of fact ("Type G, 230V") and opinion ("Bloomsbury —
 * Georgian squares, bookshops") that had no business being bundled into an
 * offline app with no way to correct it.
 *
 * What is here instead is only the part that is a fact about a country, is
 * true for the whole of it, and does not go stale between releases: what the
 * money is, what the plug is, what number to call, and which side of the road
 * to look down before crossing. It covers every country in the bundled station
 * directory, which is to say everywhere a flight in this app can land.
 *
 * A caveat worth stating: emergency numbers are the commonly published primary
 * number, and several countries run separate numbers for police, fire and
 * ambulance. Where a country is in the 112 or 911 system that number is
 * preferred, because it is the one that works from a foreign handset.
 */
object Countries {

    data class Country(
        /** ISO 3166-1 alpha-2, which is what the station directory carries. */
        val code: String,
        val name: String,
        val currencyCode: String,
        val currencyName: String,
        /** Socket letters, in the conventional A–O lettering. */
        val plugTypes: List<String>,
        val volts: Int,
        val hertz: Int,
        val emergencyNumber: String,
        val drivesOnLeft: Boolean,
    ) {
        val currency: String get() = "$currencyName ($currencyCode)"

        val power: String
            get() = "Type ${plugTypes.joinToString(" / ")} · ${volts}V / ${hertz}Hz"

        val driving: String
            get() = if (drivesOnLeft) "Drives on the left" else "Drives on the right"
    }

    private fun c(
        code: String,
        name: String,
        currencyCode: String,
        currencyName: String,
        plugs: String,
        volts: Int,
        hertz: Int,
        emergency: String,
        left: Boolean,
    ) = Country(code, name, currencyCode, currencyName, plugs.split(","), volts, hertz, emergency, left)

    private val entries: List<Country> = listOf(
        c("AD", "Andorra", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("AE", "United Arab Emirates", "AED", "UAE dirham", "C,D,G", 230, 50, "999", false),
        c("AF", "Afghanistan", "AFN", "Afghani", "C,F", 220, 50, "119", false),
        c("AG", "Antigua and Barbuda", "XCD", "East Caribbean dollar", "A,B", 230, 60, "911", true),
        c("AI", "Anguilla", "XCD", "East Caribbean dollar", "A,B", 110, 60, "911", true),
        c("AL", "Albania", "ALL", "Lek", "C,F", 230, 50, "112", false),
        c("AM", "Armenia", "AMD", "Dram", "C,F", 230, 50, "112", false),
        c("AO", "Angola", "AOA", "Kwanza", "C,F", 220, 50, "113", false),
        c("AR", "Argentina", "ARS", "Argentine peso", "C,I", 220, 50, "911", false),
        c("AS", "American Samoa", "USD", "US dollar", "A,B,F,I", 120, 60, "911", false),
        c("AT", "Austria", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("AU", "Australia", "AUD", "Australian dollar", "I", 230, 50, "000", true),
        c("AW", "Aruba", "AWG", "Aruban florin", "A,B,F", 127, 60, "911", false),
        c("AZ", "Azerbaijan", "AZN", "Manat", "C,F", 220, 50, "112", false),
        c("BA", "Bosnia and Herzegovina", "BAM", "Convertible mark", "C,F", 230, 50, "112", false),
        c("BB", "Barbados", "BBD", "Barbadian dollar", "A,B", 115, 50, "911", true),
        c("BD", "Bangladesh", "BDT", "Taka", "C,D,G,K", 220, 50, "999", true),
        c("BE", "Belgium", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("BF", "Burkina Faso", "XOF", "West African CFA franc", "C,E", 220, 50, "17", false),
        c("BG", "Bulgaria", "BGN", "Lev", "C,F", 230, 50, "112", false),
        c("BH", "Bahrain", "BHD", "Bahraini dinar", "G", 230, 50, "999", false),
        c("BI", "Burundi", "BIF", "Burundian franc", "C,E", 220, 50, "112", false),
        c("BJ", "Benin", "XOF", "West African CFA franc", "C,E", 220, 50, "117", false),
        c("BL", "Saint Barthélemy", "EUR", "Euro", "C,E", 230, 60, "112", false),
        c("BM", "Bermuda", "BMD", "Bermudian dollar", "A,B", 120, 60, "911", true),
        c("BN", "Brunei", "BND", "Brunei dollar", "G", 240, 50, "991", true),
        c("BO", "Bolivia", "BOB", "Boliviano", "A,C", 230, 50, "110", false),
        c("BQ", "Caribbean Netherlands", "USD", "US dollar", "A,C,F", 127, 50, "911", false),
        c("BR", "Brazil", "BRL", "Real", "C,N", 127, 60, "190", false),
        c("BS", "Bahamas", "BSD", "Bahamian dollar", "A,B", 120, 60, "911", true),
        c("BT", "Bhutan", "BTN", "Ngultrum", "C,D,G", 230, 50, "113", true),
        c("BW", "Botswana", "BWP", "Pula", "D,G,M", 230, 50, "999", true),
        c("BY", "Belarus", "BYN", "Belarusian rouble", "C,F", 230, 50, "112", false),
        c("BZ", "Belize", "BZD", "Belize dollar", "A,B,G", 110, 60, "911", false),
        c("CA", "Canada", "CAD", "Canadian dollar", "A,B", 120, 60, "911", false),
        c("CC", "Cocos (Keeling) Islands", "AUD", "Australian dollar", "I", 230, 50, "000", true),
        c("CD", "DR Congo", "CDF", "Congolese franc", "C,D,E", 220, 50, "112", false),
        c("CF", "Central African Republic", "XAF", "Central African CFA franc", "C,E", 220, 50, "117", false),
        c("CG", "Congo", "XAF", "Central African CFA franc", "C,E", 230, 50, "117", false),
        c("CH", "Switzerland", "CHF", "Swiss franc", "C,J", 230, 50, "112", false),
        c("CI", "Côte d'Ivoire", "XOF", "West African CFA franc", "C,E", 230, 50, "170", false),
        c("CK", "Cook Islands", "NZD", "New Zealand dollar", "I", 240, 50, "999", true),
        c("CL", "Chile", "CLP", "Chilean peso", "C,L", 220, 50, "133", false),
        c("CM", "Cameroon", "XAF", "Central African CFA franc", "C,E", 220, 50, "117", false),
        c("CN", "China", "CNY", "Renminbi", "A,C,I", 220, 50, "110", false),
        c("CO", "Colombia", "COP", "Colombian peso", "A,B", 110, 60, "123", false),
        c("CR", "Costa Rica", "CRC", "Colón", "A,B", 120, 60, "911", false),
        c("CU", "Cuba", "CUP", "Cuban peso", "A,B,C,L", 110, 60, "106", false),
        c("CV", "Cape Verde", "CVE", "Cape Verdean escudo", "C,F", 220, 50, "132", false),
        c("CW", "Curaçao", "ANG", "Netherlands Antillean guilder", "A,B", 127, 50, "911", false),
        c("CX", "Christmas Island", "AUD", "Australian dollar", "I", 230, 50, "000", true),
        c("CY", "Cyprus", "EUR", "Euro", "G", 240, 50, "112", true),
        c("CZ", "Czechia", "CZK", "Koruna", "C,E", 230, 50, "112", false),
        c("DE", "Germany", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("DJ", "Djibouti", "DJF", "Djiboutian franc", "C,E", 220, 50, "17", false),
        c("DK", "Denmark", "DKK", "Danish krone", "C,E,F,K", 230, 50, "112", false),
        c("DM", "Dominica", "XCD", "East Caribbean dollar", "D,G", 230, 50, "999", true),
        c("DO", "Dominican Republic", "DOP", "Dominican peso", "A,B", 120, 60, "911", false),
        c("DZ", "Algeria", "DZD", "Algerian dinar", "C,F", 230, 50, "17", false),
        c("EC", "Ecuador", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("EE", "Estonia", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("EG", "Egypt", "EGP", "Egyptian pound", "C,F", 220, 50, "122", false),
        c("EH", "Western Sahara", "MAD", "Dirham", "C,E", 220, 50, "19", false),
        c("ER", "Eritrea", "ERN", "Nakfa", "C,L", 230, 50, "113", false),
        c("ES", "Spain", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("ET", "Ethiopia", "ETB", "Birr", "C,F,L", 220, 50, "991", false),
        c("FI", "Finland", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("FJ", "Fiji", "FJD", "Fijian dollar", "I", 240, 50, "911", true),
        c("FK", "Falkland Islands", "FKP", "Falkland Islands pound", "G", 240, 50, "999", true),
        c("FM", "Micronesia", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("FO", "Faroe Islands", "DKK", "Danish krone", "C,E,F,K", 230, 50, "112", false),
        c("FR", "France", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("GA", "Gabon", "XAF", "Central African CFA franc", "C", 220, 50, "1730", false),
        c("GB", "United Kingdom", "GBP", "Pound sterling", "G", 230, 50, "999", true),
        c("GD", "Grenada", "XCD", "East Caribbean dollar", "G", 230, 50, "911", true),
        c("GE", "Georgia", "GEL", "Lari", "C,F", 220, 50, "112", false),
        c("GF", "French Guiana", "EUR", "Euro", "C,D,E", 220, 50, "112", false),
        c("GG", "Guernsey", "GBP", "Pound sterling", "G", 230, 50, "999", true),
        c("GH", "Ghana", "GHS", "Cedi", "D,G", 230, 50, "191", false),
        c("GI", "Gibraltar", "GIP", "Gibraltar pound", "C,G", 240, 50, "999", false),
        c("GL", "Greenland", "DKK", "Danish krone", "C,E,F,K", 230, 50, "112", false),
        c("GM", "Gambia", "GMD", "Dalasi", "G", 230, 50, "117", false),
        c("GN", "Guinea", "GNF", "Guinean franc", "C,F,K", 220, 50, "117", false),
        c("GP", "Guadeloupe", "EUR", "Euro", "C,D,E", 230, 50, "112", false),
        c("GQ", "Equatorial Guinea", "XAF", "Central African CFA franc", "C,E", 220, 50, "114", false),
        c("GR", "Greece", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("GT", "Guatemala", "GTQ", "Quetzal", "A,B", 120, 60, "110", false),
        c("GU", "Guam", "USD", "US dollar", "A,B", 110, 60, "911", false),
        c("GW", "Guinea-Bissau", "XOF", "West African CFA franc", "C", 220, 50, "112", false),
        c("GY", "Guyana", "GYD", "Guyanese dollar", "A,B,D,G", 240, 60, "911", true),
        c("HK", "Hong Kong", "HKD", "Hong Kong dollar", "G", 220, 50, "999", true),
        c("HN", "Honduras", "HNL", "Lempira", "A,B", 120, 60, "911", false),
        c("HR", "Croatia", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("HT", "Haiti", "HTG", "Gourde", "A,B", 110, 60, "114", false),
        c("HU", "Hungary", "HUF", "Forint", "C,F", 230, 50, "112", false),
        c("ID", "Indonesia", "IDR", "Rupiah", "C,F", 230, 50, "112", true),
        c("IE", "Ireland", "EUR", "Euro", "G", 230, 50, "112", true),
        c("IL", "Israel", "ILS", "Shekel", "C,H,M", 230, 50, "100", false),
        c("IM", "Isle of Man", "GBP", "Pound sterling", "G", 230, 50, "999", true),
        c("IN", "India", "INR", "Rupee", "C,D,M", 230, 50, "112", true),
        c("IO", "British Indian Ocean Territory", "USD", "US dollar", "A,B,G", 240, 50, "911", false),
        c("IQ", "Iraq", "IQD", "Iraqi dinar", "C,D,G", 230, 50, "104", false),
        c("IR", "Iran", "IRR", "Rial", "C,F", 230, 50, "110", false),
        c("IS", "Iceland", "ISK", "Króna", "C,F", 230, 50, "112", false),
        c("IT", "Italy", "EUR", "Euro", "C,F,L", 230, 50, "112", false),
        c("JE", "Jersey", "GBP", "Pound sterling", "G", 230, 50, "999", true),
        c("JM", "Jamaica", "JMD", "Jamaican dollar", "A,B", 110, 50, "119", true),
        c("JO", "Jordan", "JOD", "Jordanian dinar", "B,C,D,F,G,J", 230, 50, "911", false),
        c("JP", "Japan", "JPY", "Yen", "A,B", 100, 50, "110", true),
        c("KE", "Kenya", "KES", "Kenyan shilling", "G", 240, 50, "999", true),
        c("KG", "Kyrgyzstan", "KGS", "Som", "C,F", 220, 50, "112", false),
        c("KH", "Cambodia", "KHR", "Riel", "A,C,G", 230, 50, "117", false),
        c("KI", "Kiribati", "AUD", "Australian dollar", "I", 240, 50, "192", true),
        c("KM", "Comoros", "KMF", "Comorian franc", "C,E", 220, 50, "17", false),
        c("KN", "Saint Kitts and Nevis", "XCD", "East Caribbean dollar", "A,B,D,G", 230, 60, "911", true),
        c("KP", "North Korea", "KPW", "Won", "A,C,F", 220, 50, "119", false),
        c("KR", "South Korea", "KRW", "Won", "C,F", 220, 60, "112", false),
        c("KW", "Kuwait", "KWD", "Kuwaiti dinar", "C,G", 240, 50, "112", false),
        c("KY", "Cayman Islands", "KYD", "Cayman Islands dollar", "A,B", 120, 60, "911", true),
        c("KZ", "Kazakhstan", "KZT", "Tenge", "C,F", 220, 50, "112", false),
        c("LA", "Laos", "LAK", "Kip", "A,B,C,E,F", 230, 50, "1191", false),
        c("LB", "Lebanon", "LBP", "Lebanese pound", "A,B,C,D,G", 220, 50, "112", false),
        c("LC", "Saint Lucia", "XCD", "East Caribbean dollar", "G", 240, 50, "999", true),
        c("LI", "Liechtenstein", "CHF", "Swiss franc", "C,J", 230, 50, "112", false),
        c("LK", "Sri Lanka", "LKR", "Sri Lankan rupee", "D,G,M", 230, 50, "119", true),
        c("LR", "Liberia", "LRD", "Liberian dollar", "A,B,C,E,F", 120, 60, "911", false),
        c("LS", "Lesotho", "LSL", "Loti", "M", 220, 50, "112", true),
        c("LT", "Lithuania", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("LU", "Luxembourg", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("LV", "Latvia", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("LY", "Libya", "LYD", "Libyan dinar", "C,L", 127, 50, "1515", false),
        c("MA", "Morocco", "MAD", "Dirham", "C,E", 220, 50, "19", false),
        c("MC", "Monaco", "EUR", "Euro", "C,D,E,F", 230, 50, "112", false),
        c("MD", "Moldova", "MDL", "Leu", "C,F", 230, 50, "112", false),
        c("ME", "Montenegro", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("MF", "Saint Martin", "EUR", "Euro", "C,E", 230, 60, "112", false),
        c("MG", "Madagascar", "MGA", "Ariary", "C,D,E,J,K", 220, 50, "117", false),
        c("MH", "Marshall Islands", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("MK", "North Macedonia", "MKD", "Denar", "C,F", 230, 50, "112", false),
        c("ML", "Mali", "XOF", "West African CFA franc", "C,E", 220, 50, "17", false),
        c("MM", "Myanmar", "MMK", "Kyat", "C,D,F,G", 230, 50, "199", false),
        c("MN", "Mongolia", "MNT", "Tögrög", "C,E", 220, 50, "105", false),
        c("MO", "Macau", "MOP", "Pataca", "D,F,G,M", 220, 50, "999", true),
        c("MP", "Northern Mariana Islands", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("MQ", "Martinique", "EUR", "Euro", "C,D,E", 220, 50, "112", false),
        c("MR", "Mauritania", "MRU", "Ouguiya", "C", 220, 50, "117", false),
        c("MS", "Montserrat", "XCD", "East Caribbean dollar", "A,B", 230, 60, "999", true),
        c("MT", "Malta", "EUR", "Euro", "G", 230, 50, "112", true),
        c("MU", "Mauritius", "MUR", "Mauritian rupee", "C,G", 230, 50, "999", true),
        c("MV", "Maldives", "MVR", "Rufiyaa", "A,C,D,G,J,K,L", 230, 50, "119", true),
        c("MW", "Malawi", "MWK", "Kwacha", "G", 230, 50, "997", true),
        c("MX", "Mexico", "MXN", "Mexican peso", "A,B", 127, 60, "911", false),
        c("MY", "Malaysia", "MYR", "Ringgit", "G", 240, 50, "999", true),
        c("MZ", "Mozambique", "MZN", "Metical", "C,F,M", 220, 50, "119", true),
        c("NA", "Namibia", "NAD", "Namibian dollar", "D,M", 220, 50, "10111", true),
        c("NC", "New Caledonia", "XPF", "CFP franc", "C,F", 220, 50, "112", false),
        c("NE", "Niger", "XOF", "West African CFA franc", "A,B,C,D,E,F", 220, 50, "17", false),
        c("NF", "Norfolk Island", "AUD", "Australian dollar", "I", 230, 50, "000", true),
        c("NG", "Nigeria", "NGN", "Naira", "D,G", 230, 50, "112", false),
        c("NI", "Nicaragua", "NIO", "Córdoba", "A,B", 120, 60, "118", false),
        c("NL", "Netherlands", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("NO", "Norway", "NOK", "Norwegian krone", "C,F", 230, 50, "112", false),
        c("NP", "Nepal", "NPR", "Nepalese rupee", "C,D,M", 230, 50, "100", true),
        c("NR", "Nauru", "AUD", "Australian dollar", "I", 240, 50, "110", true),
        c("NU", "Niue", "NZD", "New Zealand dollar", "I", 230, 50, "999", true),
        c("NZ", "New Zealand", "NZD", "New Zealand dollar", "I", 230, 50, "111", true),
        c("OM", "Oman", "OMR", "Omani rial", "G", 240, 50, "9999", false),
        c("PA", "Panama", "PAB", "Balboa", "A,B", 120, 60, "911", false),
        c("PE", "Peru", "PEN", "Sol", "A,B,C", 220, 60, "105", false),
        c("PF", "French Polynesia", "XPF", "CFP franc", "C,E", 220, 60, "112", false),
        c("PG", "Papua New Guinea", "PGK", "Kina", "I", 240, 50, "112", true),
        c("PH", "Philippines", "PHP", "Peso", "A,B,C", 220, 60, "911", false),
        c("PK", "Pakistan", "PKR", "Pakistani rupee", "C,D,G,M", 230, 50, "15", true),
        c("PL", "Poland", "PLN", "Złoty", "C,E", 230, 50, "112", false),
        c("PM", "Saint Pierre and Miquelon", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("PR", "Puerto Rico", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("PS", "Palestine", "ILS", "Shekel", "C,H", 230, 50, "100", false),
        c("PT", "Portugal", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("PW", "Palau", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("PY", "Paraguay", "PYG", "Guaraní", "C", 220, 50, "911", false),
        c("QA", "Qatar", "QAR", "Qatari riyal", "D,G", 240, 50, "999", false),
        c("RE", "Réunion", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("RO", "Romania", "RON", "Leu", "C,F", 230, 50, "112", false),
        c("RS", "Serbia", "RSD", "Serbian dinar", "C,F", 230, 50, "112", false),
        c("RU", "Russia", "RUB", "Rouble", "C,F", 220, 50, "112", false),
        c("RW", "Rwanda", "RWF", "Rwandan franc", "C,J", 230, 50, "112", false),
        c("SA", "Saudi Arabia", "SAR", "Saudi riyal", "G", 230, 60, "911", false),
        c("SB", "Solomon Islands", "SBD", "Solomon Islands dollar", "I", 220, 50, "999", true),
        c("SC", "Seychelles", "SCR", "Seychellois rupee", "G", 240, 50, "999", true),
        c("SD", "Sudan", "SDG", "Sudanese pound", "C,D", 230, 50, "999", false),
        c("SE", "Sweden", "SEK", "Swedish krona", "C,F", 230, 50, "112", false),
        c("SG", "Singapore", "SGD", "Singapore dollar", "G", 230, 50, "999", true),
        c("SH", "Saint Helena", "SHP", "Saint Helena pound", "G", 240, 50, "999", true),
        c("SI", "Slovenia", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("SK", "Slovakia", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("SL", "Sierra Leone", "SLE", "Leone", "D,G", 230, 50, "999", false),
        c("SN", "Senegal", "XOF", "West African CFA franc", "C,D,E,K", 230, 50, "17", false),
        c("SO", "Somalia", "SOS", "Somali shilling", "C", 220, 50, "888", false),
        c("SR", "Suriname", "SRD", "Surinamese dollar", "A,B,C,F", 127, 60, "115", true),
        c("SS", "South Sudan", "SSP", "South Sudanese pound", "C,D", 230, 50, "999", false),
        c("ST", "São Tomé and Príncipe", "STN", "Dobra", "C,F", 220, 50, "112", false),
        c("SV", "El Salvador", "USD", "US dollar", "A,B", 115, 60, "911", false),
        c("SX", "Sint Maarten", "ANG", "Netherlands Antillean guilder", "A,B", 110, 60, "911", false),
        c("SY", "Syria", "SYP", "Syrian pound", "C,E,L", 220, 50, "110", false),
        c("SZ", "Eswatini", "SZL", "Lilangeni", "M", 230, 50, "999", true),
        c("TC", "Turks and Caicos Islands", "USD", "US dollar", "A,B", 120, 60, "911", true),
        c("TD", "Chad", "XAF", "Central African CFA franc", "C,D,E,F", 220, 50, "17", false),
        c("TG", "Togo", "XOF", "West African CFA franc", "C", 220, 50, "117", false),
        c("TH", "Thailand", "THB", "Baht", "A,B,C,O", 220, 50, "191", true),
        c("TJ", "Tajikistan", "TJS", "Somoni", "C,F,I", 220, 50, "112", false),
        c("TL", "Timor-Leste", "USD", "US dollar", "C,E,F,I", 220, 50, "112", true),
        c("TM", "Turkmenistan", "TMT", "Manat", "B,C,F", 220, 50, "03", false),
        c("TN", "Tunisia", "TND", "Tunisian dinar", "C,E", 230, 50, "197", false),
        c("TO", "Tonga", "TOP", "Paʻanga", "I", 240, 50, "911", true),
        c("TR", "Türkiye", "TRY", "Lira", "C,F", 230, 50, "112", false),
        c("TT", "Trinidad and Tobago", "TTD", "Trinidad and Tobago dollar", "A,B", 115, 60, "999", true),
        c("TV", "Tuvalu", "AUD", "Australian dollar", "I", 220, 50, "911", true),
        c("TW", "Taiwan", "TWD", "New Taiwan dollar", "A,B", 110, 60, "110", false),
        c("TZ", "Tanzania", "TZS", "Tanzanian shilling", "D,G", 230, 50, "112", true),
        c("UA", "Ukraine", "UAH", "Hryvnia", "C,F", 230, 50, "112", false),
        c("UG", "Uganda", "UGX", "Ugandan shilling", "G", 240, 50, "999", true),
        c("UM", "United States Minor Outlying Islands", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("US", "United States", "USD", "US dollar", "A,B", 120, 60, "911", false),
        c("UY", "Uruguay", "UYU", "Uruguayan peso", "C,F,L", 230, 50, "911", false),
        c("UZ", "Uzbekistan", "UZS", "Som", "C,F", 220, 50, "112", false),
        c("VA", "Vatican City", "EUR", "Euro", "C,F,L", 230, 50, "112", false),
        c("VC", "Saint Vincent and the Grenadines", "XCD", "East Caribbean dollar", "A,C,E,G,I,K", 230, 50, "999", true),
        c("VE", "Venezuela", "VES", "Bolívar", "A,B", 120, 60, "171", false),
        c("VG", "British Virgin Islands", "USD", "US dollar", "A,B", 110, 60, "999", true),
        c("VI", "US Virgin Islands", "USD", "US dollar", "A,B", 110, 60, "911", true),
        c("VN", "Vietnam", "VND", "Đồng", "A,C,F", 220, 50, "113", false),
        c("VU", "Vanuatu", "VUV", "Vatu", "C,G,I", 220, 50, "112", false),
        c("WF", "Wallis and Futuna", "XPF", "CFP franc", "C,E", 220, 50, "112", false),
        c("WS", "Samoa", "WST", "Tālā", "I", 230, 50, "911", true),
        c("XK", "Kosovo", "EUR", "Euro", "C,F", 230, 50, "112", false),
        c("YE", "Yemen", "YER", "Yemeni rial", "A,D,G", 230, 50, "199", false),
        c("YT", "Mayotte", "EUR", "Euro", "C,E", 230, 50, "112", false),
        c("ZA", "South Africa", "ZAR", "Rand", "C,M,N", 230, 50, "10111", true),
        c("ZM", "Zambia", "ZMW", "Kwacha", "C,D,G", 230, 50, "999", true),
        c("ZW", "Zimbabwe", "ZWG", "Zimbabwe gold", "D,G", 240, 50, "999", true),
    )

    private val byCode: Map<String, Country> = entries.associateBy { it.code }

    /**
     * Names are matched loosely — case, accents and "the" all vary between the
     * hand-checked core station list and the generated directory.
     */
    private val byName: Map<String, Country> = entries.associateBy { normalise(it.name) }

    fun forCode(code: String?): Country? =
        code?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let { byCode[it] }

    fun forName(name: String?): Country? = name?.let { byName[normalise(it)] }

    /** A place carries whichever form its source used; try both. */
    fun of(place: Place?): Country? {
        val value = place?.country?.trim().orEmpty()
        if (value.isEmpty()) return null
        return forCode(value) ?: forName(value)
    }

    fun all(): List<Country> = entries

    private fun normalise(value: String): String = value
        .trim()
        .lowercase()
        .removePrefix("the ")
        .map { character ->
            when (character) {
                'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
                'é', 'è', 'ê', 'ë' -> 'e'
                'í', 'ì', 'î', 'ï' -> 'i'
                'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'ç' -> 'c'
                'ñ' -> 'n'
                'ş' -> 's'
                else -> character
            }
        }
        .filter { it.isLetterOrDigit() }
        .joinToString("")
}
