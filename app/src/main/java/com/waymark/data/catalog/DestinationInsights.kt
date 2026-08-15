package com.waymark.data.catalog

/**
 * Local knowledge, bundled. The things a traveler wants in the twenty minutes
 * between landing and finding the train: what the money is, what the plug is,
 * what the taxi should cost, and what number to call.
 */
object DestinationInsights {

    data class Insight(
        val city: String,
        val country: String,
        val currency: String,
        val plugTypes: String,
        val voltage: String,
        val emergencyNumber: String,
        val tapWater: String,
        val tipping: String,
        val airportTransfer: String,
        val transitNote: String,
        val greeting: String,
        val seasonNote: String,
        val neighbourhoods: List<Neighbourhood>,
    )

    data class Neighbourhood(val name: String, val character: String)

    private val entries: List<Insight> = listOf(
        Insight(
            city = "London", country = "United Kingdom",
            currency = "Pound sterling (GBP)", plugTypes = "Type G", voltage = "230V / 50Hz",
            emergencyNumber = "999 or 112", tapWater = "Safe to drink",
            tipping = "Optional. 10–12.5% in restaurants, often already on the bill.",
            airportTransfer = "Heathrow: Elizabeth line to central London, about 35 minutes. The Express is faster and roughly four times the price.",
            transitNote = "Contactless card or phone at the gate — no ticket needed, and daily fares cap automatically.",
            greeting = "Hello / Cheers",
            seasonNote = "Rain is unremarkable in any month. Daylight is short from November to February.",
            neighbourhoods = listOf(
                Neighbourhood("Bloomsbury", "Georgian squares, bookshops, museums"),
                Neighbourhood("Borough", "Market food, river walks, close to the City"),
                Neighbourhood("Shoreditch", "Late openings, galleries, noise"),
            ),
        ),
        Insight(
            city = "Paris", country = "France",
            currency = "Euro (EUR)", plugTypes = "Type E", voltage = "230V / 50Hz",
            emergencyNumber = "112", tapWater = "Safe to drink",
            tipping = "Service is included. Rounding up is enough.",
            airportTransfer = "CDG: RER B to Châtelet, about 45 minutes; taxis are a fixed fare to each bank of the river.",
            transitNote = "Métro tickets are now digital on Navigo Easy; a carnet of ten is cheaper than singles.",
            greeting = "Bonjour — say it on entering a shop; it matters.",
            seasonNote = "August is quiet in a way that closes restaurants. Book ahead in May and September.",
            neighbourhoods = listOf(
                Neighbourhood("Le Marais", "Narrow streets, galleries, long lunches"),
                Neighbourhood("Canal Saint-Martin", "Evening crowds by the water"),
                Neighbourhood("Latin Quarter", "Students, cinemas, cheap wine"),
            ),
        ),
        Insight(
            city = "Tokyo", country = "Japan",
            currency = "Japanese yen (JPY)", plugTypes = "Type A / B", voltage = "100V / 50Hz",
            emergencyNumber = "110 police, 119 fire and ambulance", tapWater = "Safe to drink",
            tipping = "Not practised. Leaving money can cause confusion.",
            airportTransfer = "Narita: Skyliner to Ueno in 41 minutes. Haneda: monorail to Hamamatsuchō in 15.",
            transitNote = "Suica or Pasmo on the phone covers trains, buses and most convenience stores.",
            greeting = "Konnichiwa / Sumimasen (excuse me — the most useful word)",
            seasonNote = "Late June to mid-July is the rainy season. September carries typhoon risk.",
            neighbourhoods = listOf(
                Neighbourhood("Yanaka", "Low buildings, old cemeteries, quiet mornings"),
                Neighbourhood("Shimokitazawa", "Second-hand shops, small live houses"),
                Neighbourhood("Nihonbashi", "Old merchant city, close to the Ginza line"),
            ),
        ),
        Insight(
            city = "New York", country = "United States",
            currency = "US dollar (USD)", plugTypes = "Type A / B", voltage = "120V / 60Hz",
            emergencyNumber = "911", tapWater = "Safe to drink",
            tipping = "Expected. 18–20% in restaurants, a dollar or two per drink at a bar.",
            airportTransfer = "JFK: AirTrain to Jamaica, then the E train; about an hour to midtown. Yellow cabs are a flat fare to Manhattan.",
            transitNote = "OMNY: tap a card or phone at the turnstile; fares cap after twelve rides in a week.",
            greeting = "Hi / Excuse me",
            seasonNote = "August is humid. January wind off the rivers is the real cold.",
            neighbourhoods = listOf(
                Neighbourhood("West Village", "Low brownstones, crooked streets"),
                Neighbourhood("Lower East Side", "Late food, small venues"),
                Neighbourhood("Fort Greene", "Brooklyn, park-side, calmer"),
            ),
        ),
        Insight(
            city = "Singapore", country = "Singapore",
            currency = "Singapore dollar (SGD)", plugTypes = "Type G", voltage = "230V / 50Hz",
            emergencyNumber = "999 police, 995 ambulance", tapWater = "Safe to drink",
            tipping = "Not expected; a service charge is usually on the bill.",
            airportTransfer = "Changi: MRT to City Hall in about 35 minutes, or a metered taxi in 20 outside peak.",
            transitNote = "Any contactless bank card works on the MRT and buses.",
            greeting = "Hello / Lah is a suffix, not a word",
            seasonNote = "Equatorial: hot, humid, brief hard rain most afternoons.",
            neighbourhoods = listOf(
                Neighbourhood("Tiong Bahru", "Art deco blocks, bakeries, bookshops"),
                Neighbourhood("Kampong Glam", "Textile shops, cafés, the Sultan Mosque"),
                Neighbourhood("Katong", "Peranakan houses, laksa"),
            ),
        ),
        Insight(
            city = "Reykjavík", country = "Iceland",
            currency = "Icelandic króna (ISK)", plugTypes = "Type F", voltage = "230V / 50Hz",
            emergencyNumber = "112", tapWater = "Safe, and among the best anywhere. Hot water smells of sulphur; that is normal.",
            tipping = "Not expected.",
            airportTransfer = "Keflavík is 50 km out: the Flybus meets each arrival, about 45 minutes to the BSÍ terminal.",
            transitNote = "Reykjavík is walkable end to end; a car matters only for the ring road.",
            greeting = "Halló / Takk (thanks)",
            seasonNote = "Four hours of daylight in December, near-total light in June. Wind decides the weather.",
            neighbourhoods = listOf(
                Neighbourhood("Miðborg", "The old centre, harbour, and everything within a walk"),
                Neighbourhood("Vesturbær", "Residential, the good swimming pool"),
            ),
        ),
        Insight(
            city = "Lisbon", country = "Portugal",
            currency = "Euro (EUR)", plugTypes = "Type F", voltage = "230V / 50Hz",
            emergencyNumber = "112", tapWater = "Safe to drink",
            tipping = "Round up, or 5–10% for a long meal.",
            airportTransfer = "Humberto Delgado sits inside the city: metro red line, 20 minutes to the centre.",
            transitNote = "The 28 tram is transport in name and a queue in practice; the funiculars save real climbing.",
            greeting = "Bom dia / Obrigado (men) · Obrigada (women)",
            seasonNote = "July and August are hot and crowded. October is still swimming weather.",
            neighbourhoods = listOf(
                Neighbourhood("Alfama", "Steep lanes, fado, laundry lines"),
                Neighbourhood("Príncipe Real", "Gardens, design shops, calm evenings"),
            ),
        ),
        Insight(
            city = "Rome", country = "Italy",
            currency = "Euro (EUR)", plugTypes = "Type F / L", voltage = "230V / 50Hz",
            emergencyNumber = "112", tapWater = "Safe; the street fountains are drinkable and cold",
            tipping = "Not expected. Coperto is a cover charge, not a tip.",
            airportTransfer = "Fiumicino: Leonardo Express to Termini every 15 minutes, 32 minutes end to end. Taxis are a fixed city fare.",
            transitNote = "Two metro lines only; buses and walking fill the gaps.",
            greeting = "Buongiorno / Permesso",
            seasonNote = "August empties the city and closes the good restaurants. Spring rain is short.",
            neighbourhoods = listOf(
                Neighbourhood("Trastevere", "Evening crowds, ivy, cobbles"),
                Neighbourhood("Monti", "Neighbourhood bars a short walk from the Forum"),
            ),
        ),
        Insight(
            city = "Sydney", country = "Australia",
            currency = "Australian dollar (AUD)", plugTypes = "Type I", voltage = "230V / 50Hz",
            emergencyNumber = "000", tapWater = "Safe to drink",
            tipping = "Not expected; staff are paid a full wage.",
            airportTransfer = "Kingsford Smith: the airport line to Central in 13 minutes, with a station access fee that makes short taxis competitive for two or more.",
            transitNote = "Opal or a contactless card; fares cap daily and on Sundays.",
            greeting = "Morning / How are you going?",
            seasonNote = "Seasons are inverted. December to February is hot, with real UV — shade is not optional.",
            neighbourhoods = listOf(
                Neighbourhood("Surry Hills", "Terraces, coffee, small restaurants"),
                Neighbourhood("Manly", "Ferry ride, surf, quieter evenings"),
            ),
        ),
    )

    private val byCity: Map<String, Insight> = entries.associateBy { it.city.lowercase() }

    fun forCity(city: String?): Insight? = city?.trim()?.lowercase()?.let { byCity[it] }

    /** Airport code first, since that is what a flight segment carries. */
    fun forAirport(code: String?): Insight? = Airports.find(code)?.let { forCity(it.city) }

    fun all(): List<Insight> = entries
}
