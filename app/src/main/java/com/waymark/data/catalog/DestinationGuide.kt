package com.waymark.data.catalog

import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.Place
import com.waymark.domain.model.PriceBand

/**
 * What to see, walk and eat, bundled per city.
 *
 * This is a briefing, not a guidebook: a short, opinionated list per
 * destination, written the way a friend who lives there would say it. Entries
 * become saved [Idea]s with one tap and can then be scheduled onto the
 * timeline like anything else.
 *
 * Coordinates are accurate to the block, which is what a map mark needs.
 * Dishes carry no coordinates — a dish is not a place — and appear as a
 * checklist instead.
 */
object DestinationGuide {

    data class Entry(
        val title: String,
        val kind: IdeaKind,
        val note: String,
        val area: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val priceBand: PriceBand? = null,
        val typicalMinutes: Int? = null,
        val bestTime: String? = null,
    )

    private val byCity: Map<String, List<Entry>> = mapOf(
        "London" to listOf(
            Entry(
                "Sir John Soane's Museum", IdeaKind.SIGHT,
                "An architect's house left exactly as he died in it, crammed floor to ceiling. Free, timed entry.",
                area = "Holborn", latitude = 51.5170, longitude = -0.1170,
                priceBand = PriceBand.FREE, typicalMinutes = 90,
                bestTime = "First slot; the rooms are small and fill quickly.",
            ),
            Entry(
                "The Wallace Collection", IdeaKind.SIGHT,
                "A townhouse of armour and Fragonards behind Oxford Street, and almost never busy.",
                area = "Marylebone", latitude = 51.5175, longitude = -0.1536,
                priceBand = PriceBand.FREE, typicalMinutes = 90,
            ),
            Entry(
                "Sky Garden", IdeaKind.SIGHT,
                "The free view. Book a slot weeks out; the paid alternatives are not better.",
                area = "City", latitude = 51.5111, longitude = -0.0836,
                priceBand = PriceBand.FREE, typicalMinutes = 60,
                bestTime = "Late afternoon, for the light going down the river.",
            ),
            Entry(
                "Regent's Canal: Angel to Broadway Market", IdeaKind.WALK,
                "An hour of towpath, narrowboats and the back of the city.",
                area = "Islington to Hackney", latitude = 51.5320, longitude = -0.1050,
                priceBand = PriceBand.FREE, typicalMinutes = 75,
            ),
            Entry(
                "Borough Market", IdeaKind.EATERY,
                "Go hungry, buy from three stalls, eat on the churchyard wall.",
                area = "Southwark", latitude = 51.5055, longitude = -0.0910,
                priceBand = PriceBand.LOW, typicalMinutes = 75,
                bestTime = "Weekday lunch. Closed Sundays.",
            ),
            Entry(
                "St. John", IdeaKind.EATERY,
                "Nose-to-tail, whitewashed walls, the bone marrow and parsley salad.",
                area = "Smithfield", latitude = 51.5203, longitude = -0.1017,
                priceBand = PriceBand.HIGH, typicalMinutes = 120,
                bestTime = "Book. The bar takes walk-ins.",
            ),
            Entry(
                "A proper Sunday roast", IdeaKind.DISH,
                "Beef, a Yorkshire pudding the size of a hat, gravy. Pubs serve it until it runs out.",
                priceBand = PriceBand.MEDIUM,
                bestTime = "Sunday, book for 13:00 or accept what is left.",
            ),
            Entry(
                "Salt beef beigel, Brick Lane", IdeaKind.DISH,
                "Open all night, cash-quick queue, mustard and pickle. Two pounds of history.",
                area = "Shoreditch", latitude = 51.5238, longitude = -0.0716,
                priceBand = PriceBand.LOW,
            ),
        ),

        "Paris" to listOf(
            Entry(
                "Musée de l'Orangerie", IdeaKind.SIGHT,
                "Two oval rooms of Nymphéas, built to Monet's instructions. Everything else is a bonus.",
                area = "Tuileries", latitude = 48.8638, longitude = 2.3226,
                priceBand = PriceBand.LOW, typicalMinutes = 90,
                bestTime = "Opening, before the coaches.",
            ),
            Entry(
                "Musée Rodin", IdeaKind.SIGHT,
                "Buy the garden-only ticket and sit with The Thinker for an hour.",
                area = "7e", latitude = 48.8553, longitude = 2.3158,
                priceBand = PriceBand.LOW, typicalMinutes = 75,
            ),
            Entry(
                "Sainte-Chapelle", IdeaKind.SIGHT,
                "Fifteen metres of thirteenth-century glass. The queue is for the security check, not the chapel.",
                area = "Île de la Cité", latitude = 48.8554, longitude = 2.3450,
                priceBand = PriceBand.LOW, typicalMinutes = 45,
                bestTime = "A bright morning; the whole point is sunlight.",
            ),
            Entry(
                "Coulée verte René-Dumont", IdeaKind.WALK,
                "A viaduct turned linear garden, four kilometres above the traffic. The one New York copied.",
                area = "12e", latitude = 48.8494, longitude = 2.3730,
                priceBand = PriceBand.FREE, typicalMinutes = 90,
            ),
            Entry(
                "Marché d'Aligre", IdeaKind.EATERY,
                "Covered market, street stalls and a wine bar that has been there forever.",
                area = "12e", latitude = 48.8492, longitude = 2.3778,
                priceBand = PriceBand.LOW, typicalMinutes = 60,
                bestTime = "Morning, Tuesday to Sunday.",
            ),
            Entry(
                "Le Baratin", IdeaKind.EATERY,
                "Belleville, up the hill, a chalkboard and no fuss. Telephone bookings only.",
                area = "Belleville", latitude = 48.8722, longitude = 2.3877,
                priceBand = PriceBand.MEDIUM, typicalMinutes = 150,
            ),
            Entry(
                "Jambon-beurre", IdeaKind.DISH,
                "Ham, butter, baguette. Judged entirely on the bread; buy it from a bakery, not a café.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "A croissant worth the trip", IdeaKind.DISH,
                "Judge it by the ends: they should shatter. If the middle is doughy, walk on.",
                priceBand = PriceBand.LOW,
                bestTime = "Before ten, still warm.",
            ),
        ),

        "Tokyo" to listOf(
            Entry(
                "Nezu Museum", IdeaKind.SIGHT,
                "A quiet collection and, behind it, a garden that makes the city vanish.",
                area = "Aoyama", latitude = 35.6626, longitude = 139.7167,
                priceBand = PriceBand.LOW, typicalMinutes = 90,
            ),
            Entry(
                "Meiji Jingū", IdeaKind.SIGHT,
                "A forest planted by hand a century ago. Walk in from Harajuku and out at Yoyogi.",
                area = "Shibuya", latitude = 35.6764, longitude = 139.6993,
                priceBand = PriceBand.FREE, typicalMinutes = 75,
                bestTime = "Early. It opens at sunrise and is empty until eight.",
            ),
            Entry(
                "Yanaka: Ginza and the cemetery", IdeaKind.WALK,
                "Low buildings, cats, a shopping street that survived the war, and cherry trees over graves.",
                area = "Yanaka", latitude = 35.7276, longitude = 139.7660,
                priceBand = PriceBand.FREE, typicalMinutes = 120,
            ),
            Entry(
                "Tsukiji Outer Market", IdeaKind.EATERY,
                "The inner market moved; the outer one still feeds everyone. Tamagoyaki on a stick.",
                area = "Tsukiji", latitude = 35.6654, longitude = 139.7707,
                priceBand = PriceBand.LOW, typicalMinutes = 90,
                bestTime = "Before nine.",
            ),
            Entry(
                "A sentō bathhouse", IdeaKind.ACTIVITY,
                "Neighbourhood bath, hot beyond reason. Wash thoroughly first, tattoos may need covering.",
                priceBand = PriceBand.LOW, typicalMinutes = 60,
                bestTime = "Evening, after walking all day.",
            ),
            Entry(
                "Standing soba at a station stand", IdeaKind.DISH,
                "Ticket machine, ninety seconds, three hundred yen. Eat it fast, standing, like everyone else.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "Convenience-store egg sandwich", IdeaKind.DISH,
                "Not a joke. Crustless, absurdly good, and available at four in the morning.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "Kissaten coffee and thick toast", IdeaKind.DISH,
                "An old-style coffee house: siphon brew, smoke-stained wood, toast cut like a brick.",
                priceBand = PriceBand.LOW,
                bestTime = "Mid-morning.",
            ),
        ),

        "New York" to listOf(
            Entry(
                "The Morgan Library", IdeaKind.SIGHT,
                "A banker's private library kept as it was: three storeys of walnut and a Gutenberg Bible.",
                area = "Murray Hill", latitude = 40.7492, longitude = -73.9814,
                priceBand = PriceBand.MEDIUM, typicalMinutes = 90,
            ),
            Entry(
                "Grand Central's whispering gallery", IdeaKind.SIGHT,
                "Outside the Oyster Bar. Stand at opposite corners of the arch and talk into the tiles.",
                area = "Midtown", latitude = 40.7527, longitude = -73.9772,
                priceBand = PriceBand.FREE, typicalMinutes = 20,
            ),
            Entry(
                "Brooklyn Bridge at dawn", IdeaKind.WALK,
                "The only hour it is yours. Walk from the Brooklyn side so the skyline arrives ahead of you.",
                area = "Dumbo", latitude = 40.7061, longitude = -73.9969,
                priceBand = PriceBand.FREE, typicalMinutes = 60,
                bestTime = "Sunrise, and not a minute later.",
            ),
            Entry(
                "The High Line", IdeaKind.WALK,
                "Freight line turned garden. Enter at Gansevoort, leave when the crowd annoys you.",
                area = "Chelsea", latitude = 40.7480, longitude = -74.0048,
                priceBand = PriceBand.FREE, typicalMinutes = 60,
            ),
            Entry(
                "Katz's Delicatessen", IdeaKind.EATERY,
                "Take the ticket, tip the carver, order pastrami on rye. Do not lose the ticket.",
                area = "Lower East Side", latitude = 40.7223, longitude = -73.9874,
                priceBand = PriceBand.MEDIUM, typicalMinutes = 60,
            ),
            Entry(
                "A slice, folded, standing up", IdeaKind.DISH,
                "Plain cheese, from a counter with no seats. Fold it lengthways or be identified as a tourist.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "Bagel with scallion cream cheese", IdeaKind.DISH,
                "Boiled, not steamed. Ask for it scooped only if you want to start an argument.",
                priceBand = PriceBand.LOW,
                bestTime = "Before eleven, weekends excepted.",
            ),
        ),

        "Singapore" to listOf(
            Entry(
                "Maxwell Food Centre", IdeaKind.EATERY,
                "Hawker centre in the old town. Queue at whichever stall the office workers are queueing at.",
                area = "Chinatown", latitude = 1.2803, longitude = 103.8449,
                priceBand = PriceBand.LOW, typicalMinutes = 60,
                bestTime = "Just before noon, or after two.",
            ),
            Entry(
                "Botanic Gardens", IdeaKind.SIGHT,
                "Free, enormous, and older than the country. The orchid garden is the one paid corner.",
                area = "Tanglin", latitude = 1.3138, longitude = 103.8159,
                priceBand = PriceBand.FREE, typicalMinutes = 120,
                bestTime = "Early morning, before the heat.",
            ),
            Entry(
                "Gardens by the Bay", IdeaKind.SIGHT,
                "The supertrees are free to walk under; the domes are worth the ticket on a hot afternoon.",
                area = "Marina Bay", latitude = 1.2816, longitude = 103.8636,
                priceBand = PriceBand.MEDIUM, typicalMinutes = 150,
                bestTime = "Dusk, for the light show at 19:45.",
            ),
            Entry(
                "Southern Ridges", IdeaKind.WALK,
                "Ten kilometres of forest bridges between hills, mostly in shade.",
                area = "Telok Blangah", latitude = 1.2782, longitude = 103.8010,
                priceBand = PriceBand.FREE, typicalMinutes = 150,
            ),
            Entry(
                "Hainanese chicken rice", IdeaKind.DISH,
                "Poached chicken, rice cooked in the stock, three sauces. The rice is the dish.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "Kaya toast with soft eggs", IdeaKind.DISH,
                "Breakfast: coconut jam on charcoal toast, two eggs barely set, dark soy and white pepper.",
                priceBand = PriceBand.LOW,
                bestTime = "Before nine.",
            ),
            Entry(
                "Chilli crab", IdeaKind.DISH,
                "Dinner, shared, messy. Order the fried mantou buns to mop the sauce.",
                priceBand = PriceBand.HIGH,
            ),
        ),

        "Reykjavík" to listOf(
            Entry(
                "Sundhöll Reykjavíkur", IdeaKind.ACTIVITY,
                "The city pool: lanes outdoors, hot pots at 40°C, everyone from toddlers to pensioners. Shower naked first; it is not negotiable.",
                area = "Miðborg", latitude = 64.1440, longitude = -21.9265,
                priceBand = PriceBand.LOW, typicalMinutes = 90,
                bestTime = "Evening, in bad weather especially.",
            ),
            Entry(
                "Hallgrímskirkja tower", IdeaKind.SIGHT,
                "A lift up the concrete basalt column for the only overview of the coloured roofs.",
                area = "Miðborg", latitude = 64.1417, longitude = -21.9266,
                priceBand = PriceBand.LOW, typicalMinutes = 45,
            ),
            Entry(
                "Grótta lighthouse", IdeaKind.WALK,
                "The edge of town, out on a spit. Check the tide — the causeway floods — and stay for the sky.",
                area = "Seltjarnarnes", latitude = 64.1631, longitude = -22.0212,
                priceBand = PriceBand.FREE, typicalMinutes = 90,
                bestTime = "After dark in winter; it is the darkest spot near the city.",
            ),
            Entry(
                "Kolaportið flea market", IdeaKind.SHOP,
                "Weekends only. Wool, second-hand books, and the fermented shark you were warned about.",
                area = "Harbour", latitude = 64.1481, longitude = -21.9370,
                priceBand = PriceBand.LOW, typicalMinutes = 60,
            ),
            Entry(
                "Kjötsúpa — lamb soup", IdeaKind.DISH,
                "Root vegetables and lamb on the bone. The thing to eat after being cold all day.",
                priceBand = PriceBand.MEDIUM,
            ),
            Entry(
                "Skyr, plain", IdeaKind.DISH,
                "Not yoghurt — a fresh cheese, thick and sour. Buy it in a supermarket for a fifth of café prices.",
                priceBand = PriceBand.LOW,
            ),
        ),

        "Lisbon" to listOf(
            Entry(
                "Gulbenkian Museum", IdeaKind.SIGHT,
                "One collector's taste, from Egyptian bronzes to Lalique, in a modernist building with a garden.",
                area = "Avenidas Novas", latitude = 38.7376, longitude = -9.1537,
                priceBand = PriceBand.LOW, typicalMinutes = 120,
            ),
            Entry(
                "Alfama miradouros", IdeaKind.WALK,
                "Climb from Sé to Graça by whichever lane looks worst. Three viewpoints, one hill, no shortcuts.",
                area = "Alfama", latitude = 38.7139, longitude = -9.1284,
                priceBand = PriceBand.FREE, typicalMinutes = 120,
                bestTime = "Late afternoon, going up as the light goes orange.",
            ),
            Entry(
                "Cervejaria Ramiro", IdeaKind.EATERY,
                "Shellfish, beer, paper tablecloths. Finish with the steak sandwich; that is the order.",
                area = "Intendente", latitude = 38.7204, longitude = -9.1354,
                priceBand = PriceBand.HIGH, typicalMinutes = 120,
                bestTime = "Take a ticket and wait; they do not take bookings.",
            ),
            Entry(
                "Fado in a small room", IdeaKind.ACTIVITY,
                "Not a dinner show. A back room, three musicians, silence expected while they sing.",
                area = "Alfama", priceBand = PriceBand.MEDIUM, typicalMinutes = 120,
                bestTime = "After 21:00.",
            ),
            Entry(
                "Pastel de nata, warm", IdeaKind.DISH,
                "Cinnamon on top, eaten standing at the counter. Cold ones are a waste of a walk.",
                priceBand = PriceBand.LOW,
            ),
            Entry(
                "Bifana", IdeaKind.DISH,
                "Marinated pork in a bread roll, mustard optional, beer compulsory. Two euros of lunch.",
                priceBand = PriceBand.LOW,
            ),
        ),

        "Rome" to listOf(
            Entry(
                "The Pantheon, early", IdeaKind.SIGHT,
                "Two thousand years old and still the largest unreinforced concrete dome on earth. Go at opening or in rain.",
                area = "Centro", latitude = 41.8986, longitude = 12.4769,
                priceBand = PriceBand.LOW, typicalMinutes = 45,
                bestTime = "Opening, or during a downpour — the oculus is open to the sky.",
            ),
            Entry(
                "Galleria Borghese", IdeaKind.SIGHT,
                "Bernini's marble that behaves like flesh. Two-hour slots, booked ahead, no exceptions.",
                area = "Villa Borghese", latitude = 41.9142, longitude = 12.4922,
                priceBand = PriceBand.MEDIUM, typicalMinutes = 120,
                bestTime = "Book weeks out.",
            ),
            Entry(
                "Appia Antica on a Sunday", IdeaKind.WALK,
                "The old road, closed to traffic on Sundays: basalt paving, umbrella pines, tombs in fields.",
                area = "South", latitude = 41.8562, longitude = 12.5169,
                priceBand = PriceBand.FREE, typicalMinutes = 180,
            ),
            Entry(
                "Testaccio market", IdeaKind.EATERY,
                "A working neighbourhood market with a row of lunch counters at the back.",
                area = "Testaccio", latitude = 41.8770, longitude = 12.4750,
                priceBand = PriceBand.LOW, typicalMinutes = 60,
                bestTime = "Lunch, Monday to Saturday.",
            ),
            Entry(
                "Cacio e pepe", IdeaKind.DISH,
                "Three ingredients and nowhere to hide. If it is creamy without cream, they can cook.",
                priceBand = PriceBand.MEDIUM,
            ),
            Entry(
                "Supplì", IdeaKind.DISH,
                "Fried rice ball with a mozzarella core, eaten standing outside the pizzeria that sold it.",
                priceBand = PriceBand.LOW,
            ),
        ),

        "Sydney" to listOf(
            Entry(
                "Bondi to Coogee coastal walk", IdeaKind.WALK,
                "Six kilometres of cliff path, four beaches, one cemetery with the best view in the city.",
                area = "Eastern beaches", latitude = -33.8915, longitude = 151.2767,
                priceBand = PriceBand.FREE, typicalMinutes = 150,
                bestTime = "Early; there is almost no shade.",
            ),
            Entry(
                "Art Gallery of New South Wales", IdeaKind.SIGHT,
                "Free collection, strong on Australian and Aboriginal work, in two buildings joined underground.",
                area = "The Domain", latitude = -33.8688, longitude = 151.2170,
                priceBand = PriceBand.FREE, typicalMinutes = 120,
            ),
            Entry(
                "Wylie's Baths", IdeaKind.ACTIVITY,
                "An ocean pool on the rocks at Coogee, waves breaking into it at high tide.",
                area = "Coogee", latitude = -33.9226, longitude = 151.2597,
                priceBand = PriceBand.LOW, typicalMinutes = 60,
            ),
            Entry(
                "Botanic Garden to Mrs Macquarie's Chair", IdeaKind.WALK,
                "Around the harbour edge, with the bridge and the Opera House lining up at the end.",
                area = "Circular Quay", latitude = -33.8599, longitude = 151.2220,
                priceBand = PriceBand.FREE, typicalMinutes = 75,
                bestTime = "Sunset.",
            ),
            Entry(
                "Fish and chips at Watsons Bay", IdeaKind.DISH,
                "Ferry out, eat on the grass, ferry back. The journey is most of the meal.",
                area = "Watsons Bay", latitude = -33.8419, longitude = 151.2817,
                priceBand = PriceBand.MEDIUM,
            ),
            Entry(
                "A flat white, properly made", IdeaKind.DISH,
                "This is where it comes from. Order it in the morning; after noon nobody will judge, but they will notice.",
                priceBand = PriceBand.LOW,
            ),
        ),
    )

    fun cities(): Set<String> = byCity.keys

    fun forCity(city: String?): List<Entry> =
        byCity.entries.firstOrNull { it.key.equals(city?.trim(), ignoreCase = true) }?.value
            ?: emptyList()

    fun forAirport(code: String?): List<Entry> = Airports.find(code)?.let { forCity(it.city) }.orEmpty()

    fun all(): List<Entry> = byCity.values.flatten()

    /** Turn a guide entry into a saved idea for a trip. */
    fun Entry.toIdea(id: String, tripId: String, city: String): Idea = Idea(
        id = id,
        tripId = tripId,
        title = title,
        kind = kind,
        city = city,
        place = if (latitude != null && longitude != null) {
            Place(
                name = title,
                city = city,
                latitude = latitude,
                longitude = longitude,
                timeZoneId = zoneFor(city),
                address = area,
            )
        } else {
            null
        },
        note = note,
        priceBand = priceBand,
        typicalMinutes = typicalMinutes,
        bestTime = bestTime,
        source = Idea.SOURCE_GUIDE,
    )

    /** Guide cities are all served by the bundled station list. */
    private fun zoneFor(city: String): String =
        Airports.all().firstOrNull { it.city.equals(city, ignoreCase = true) }?.timeZoneId ?: "UTC"
}
