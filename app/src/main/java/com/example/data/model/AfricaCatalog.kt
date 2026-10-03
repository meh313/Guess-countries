package com.example.data.model

/**
 * The countries of Africa as the UN M49 geoscheme defines the region.
 * Entries are alphabetical by name; tools/gen-entries.py inserts new ones in place.
 */
object AfricaCatalog {
    val countries: List<Country> = listOf(
        Country(
            code = "EG",
            name = "Egypt",
            officialName = "Arab Republic of Egypt",
            capital = "Cairo",
            continent = "Africa",
            subregion = "Northern Africa",
            population = 117000000L,
            areaSqKm = 1001450.0,
            flagEmoji = "🇪🇬",
            flagColors = listOf("Red", "White", "Black", "Gold"),
            flagDescription = "The red, white and black bands come from the Arab Liberation Flag of the 1952 revolution. Its leaders explained red as the blood Egyptians shed fighting colonial rule, white as pure hearts, and black as darkness overcome. The gold eagle in the middle is called the Eagle of Saladin, after a 12th-century ruler of Egypt. It replaced a gold hawk in 1984.",
            languages = listOf("Arabic"),
            currency = "Egyptian Pound (E£)",
            landmarks = listOf("Great Pyramids of Giza", "Sphinx", "Luxor Temple"),
            funFact = "The Great Pyramid of Giza was the world's tallest human-made structure for more than 3,700 years."
        ),
        Country(
            code = "KE",
            name = "Kenya",
            officialName = "Republic of Kenya",
            capital = "Nairobi",
            continent = "Africa",
            subregion = "Eastern Africa",
            population = 56400000L,
            areaSqKm = 580367.0,
            flagEmoji = "🇰🇪",
            flagColors = listOf("Black", "Red", "Green", "White"),
            flagDescription = "Kenya adopted this flag at independence in 1963. The black, red and green bands come from the flag of KANU, the party that led the country to independence. Officially, black stands for the people, red for the blood shed in the fight for independence, green for the land and white for peace. The thin white edges keep the bands apart. The Maasai shield and crossed spears stand for defending freedom.",
            languages = listOf("Swahili", "English"),
            currency = "Kenyan Shilling (KSh)",
            landmarks = listOf("Maasai Mara National Reserve", "Mount Kenya", "Amboseli National Park"),
            funFact = "Each year over a million wildebeest travel between Tanzania's Serengeti and Kenya's Maasai Mara, braving crocodiles as they cross the Mara River.",
            driveSide = "Left"
        ),
        Country(
            code = "MA",
            name = "Morocco",
            officialName = "Kingdom of Morocco",
            capital = "Rabat",
            continent = "Africa",
            subregion = "Northern Africa",
            population = 38100000L,
            areaSqKm = 446550.0,
            flagEmoji = "🇲🇦",
            flagColors = listOf("Red", "Green"),
            flagDescription = "Morocco's flag was plain red from the 1600s. Red is linked to the Alaouite ruling family, who trace their ancestry to the Prophet Muhammad. In 1915 a royal decree added the green star, called the Seal of Solomon, so the flag would not be mistaken for other similar flags, especially at sea. Its five points are often linked to the five pillars of Islam. No law gives a meaning for the colors.",
            languages = listOf("Arabic", "Amazigh (Tamazight)"),
            currency = "Moroccan Dirham (DH)",
            landmarks = listOf("Chefchaouen (Blue City)", "Marrakech Medina", "Hassan II Mosque"),
            funFact = "Fez's al-Qarawiyyin, founded in 859 by Fatima al-Fihri, is listed by Guinness as the world's oldest continually operating educational institution."
        ),
        Country(
            code = "NG",
            name = "Nigeria",
            officialName = "Federal Republic of Nigeria",
            capital = "Abuja",
            continent = "Africa",
            subregion = "Western Africa",
            population = 232700000L,
            areaSqKm = 923768.0,
            flagEmoji = "🇳🇬",
            flagColors = listOf("Green", "White"),
            flagDescription = "A student in London, Michael Taiwo Akinkunmi, designed Nigeria's flag and won a nationwide contest in 1959. Green stands for farming. White stands for unity and peace. His design also had a red sun on the white band, meant for divine protection and guidance, but the committee left it out. No reason is given for the three bands. It became official at independence, on October 1, 1960.",
            languages = listOf("English", "Hausa", "Yoruba", "Igbo"),
            currency = "Nigerian Naira (₦)",
            landmarks = listOf("Zuma Rock", "Lekki Conservation Centre", "Osun-Osogbo Sacred Grove"),
            funFact = "Nigeria's Nollywood film industry took off in the 1990s with low-budget home videos and ranks among the world's most prolific by number of films."
        ),
        Country(
            code = "ZA",
            name = "South Africa",
            officialName = "Republic of South Africa",
            capital = "Pretoria (executive), Cape Town (legislative), Bloemfontein (judicial)",
            continent = "Africa",
            subregion = "Southern Africa",
            population = 64000000L,
            areaSqKm = 1219090.0,
            flagEmoji = "🇿🇦",
            flagColors = listOf("Black", "Gold", "Green", "White", "Red", "Blue"),
            flagDescription = "Fred Brownell designed the flag, first used in 1994 for the country's first democratic elections. Officially, the green Y can be read as different parts of society coming together and moving ahead in unity. The government says no single meaning should be given to any color. Red, white and blue come from older flags tied to Britain and the Netherlands. Black, gold and green are also colors of the African National Congress flag.",
            languages = listOf("Zulu", "Xhosa", "Afrikaans", "English", "Northern Sotho", "Southern Sotho", "Tswana", "Tsonga", "Swati", "Venda", "Southern Ndebele", "South African Sign Language"),
            currency = "South African Rand (R)",
            landmarks = listOf("Table Mountain", "Kruger National Park", "Robben Island"),
            funFact = "South Africa completely surrounds another country: the small mountain kingdom of Lesotho lies entirely inside its borders.",
            driveSide = "Left",
            quizCapital = "Pretoria"
        ),
        Country(
            code = "TZ",
            name = "Tanzania",
            officialName = "United Republic of Tanzania",
            capital = "Dodoma",
            continent = "Africa",
            subregion = "Eastern Africa",
            population = 68600000L,
            areaSqKm = 947303.0,
            flagEmoji = "🇹🇿",
            flagColors = listOf("Green", "Yellow", "Black", "Blue"),
            flagDescription = "Tanzania's flag dates from 1964, when Tanganyika and Zanzibar joined to form one country. It mixes their flags. Green, black and the thin yellow edges come from Tanganyika's flag. Blue comes from Zanzibar's. The diagonal layout makes it different from both. Green is said to stand for the land, black for the Swahili people, yellow for mineral wealth and blue for the Indian Ocean, lakes and rivers.",
            languages = listOf("Swahili", "English"),
            currency = "Tanzanian Shilling (TSh)",
            landmarks = listOf("Mount Kilimanjaro", "Serengeti National Park", "Stone Town (Zanzibar)"),
            funFact = "Tanzania contains Mount Kilimanjaro, Africa's highest mountain peak at 5,895 meters above sea level.",
            driveSide = "Left"
        )
    )
}
