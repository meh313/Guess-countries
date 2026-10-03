package com.example.data.model

/**
 * The countries of Oceania as the UN M49 geoscheme defines the region.
 * Entries are alphabetical by name; tools/gen-entries.py inserts new ones in place.
 */
object OceaniaCatalog {
    val countries: List<Country> = listOf(
        Country(
            code = "AU",
            name = "Australia",
            officialName = "Commonwealth of Australia",
            capital = "Canberra",
            continent = "Oceania",
            subregion = "Australia and New Zealand",
            population = 26700000L,
            areaSqKm = 7741220.0,
            flagEmoji = "🇦🇺",
            flagColors = listOf("Blue", "White", "Red"),
            flagDescription = "The flag is based on a British ship flag, which is why it has a blue background and a Union Jack in the upper left corner. The Union Jack recalls Australia's history of British settlement. The big star has seven points: six for the six states and one for the territories. The five smaller stars are the Southern Cross, a star group seen in southern skies. The blue has no official meaning. The design began in a 1901 contest.",
            languages = listOf("English"),
            currency = "Australian Dollar (A$)",
            landmarks = listOf("Sydney Opera House", "Great Barrier Reef", "Uluru (Ayers Rock)"),
            funFact = "Most of Australia's mammals and over 80% of its reptiles, frogs and flowering plants are found nowhere else on Earth.",
            driveSide = "Left"
        ),
        Country(
            code = "FJ",
            name = "Fiji",
            officialName = "Republic of Fiji",
            capital = "Suva",
            continent = "Oceania",
            subregion = "Melanesia",
            population = 929000L,
            areaSqKm = 18274.0,
            flagEmoji = "🇫🇯",
            flagColors = listOf("Light Blue", "Red", "White", "Yellow", "Green"),
            flagDescription = "The light blue stands for the Pacific Ocean, said designer Tessa Mackenzie. The Union Jack is there because Britain ruled Fiji from 1874 to 1970, when this flag was adopted. The shield comes from the coat of arms of 1908. Its red cross is England's St George's cross. The lion holds a cocoa pod. Sugar cane, a coconut palm and bananas show crops that matter to Fiji. The dove is a sign of peace.",
            languages = listOf("English", "Fijian", "Fiji Hindi"),
            currency = "Fijian Dollar (FJ$)",
            landmarks = listOf("Mamanuca Islands", "Sri Siva Subramaniya Temple", "Bouma National Heritage Park"),
            funFact = "Fiji consists of an archipelago of more than 330 islands, of which only about 110 are permanently inhabited.",
            driveSide = "Left"
        ),
        Country(
            code = "NZ",
            name = "New Zealand",
            officialName = "New Zealand",
            capital = "Wellington",
            continent = "Oceania",
            subregion = "Australia and New Zealand",
            population = 5210000L,
            areaSqKm = 268838.0,
            flagEmoji = "🇳🇿",
            flagColors = listOf("Blue", "Red", "White"),
            flagDescription = "The blue comes from a British navy flag that government ships of British colonies flew. Officials also say it brings to mind the sea and sky. The Union Jack shows that New Zealand began as a British colony. The four stars are the Southern Cross, a star group seen in southern skies. They are red with white borders, as in the Union Jack. The design dates from 1869 and became law in 1902.",
            languages = listOf("English", "Māori", "New Zealand Sign Language"),
            currency = "New Zealand Dollar (NZ$)",
            landmarks = listOf("Milford Sound", "Hobbiton Movie Set", "Aoraki / Mount Cook"),
            funFact = "In 1893, New Zealand became the first self-governing country to let all women vote in parliamentary elections.",
            driveSide = "Left"
        )
    )
}
