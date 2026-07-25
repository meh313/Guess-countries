package com.example.data.model

import com.example.data.local.UserProgressDao
import com.example.data.local.UserProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CountryRepository(private val progressDao: UserProgressDao) {

    val allCountries: List<Country> = listOf(
        // EUROPE
        Country(
            code = "FR",
            name = "France",
            officialName = "French Republic",
            capital = "Paris",
            continent = "Europe",
            subregion = "Western Europe",
            population = 67750000L,
            areaSqKm = 643801.0,
            flagEmoji = "🇫🇷",
            flagColors = listOf("Blue", "White", "Red"),
            flagType = FlagStyle.VERTICAL_STRIPES_3,
            flagDescription = "The French Tricolour features three vertical bands: blue representing liberty and Paris, white representing equality and royalty, and red for fraternity.",
            languages = listOf("French"),
            currency = "Euro (€)",
            landmarks = listOf("Eiffel Tower", "Louvre Museum", "Mont Saint-Michel"),
            funFact = "France is the most visited country in the world, attracting over 89 million international tourists annually."
        ),
        Country(
            code = "DE",
            name = "Germany",
            officialName = "Federal Republic of Germany",
            capital = "Berlin",
            continent = "Europe",
            subregion = "Western Europe",
            population = 83200000L,
            areaSqKm = 357022.0,
            flagEmoji = "🇩🇪",
            flagColors = listOf("Black", "Red", "Gold"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_3,
            flagDescription = "The tricolor of black, red, and gold originated during the 19th-century struggle for German unity and democracy.",
            languages = listOf("German"),
            currency = "Euro (€)",
            landmarks = listOf("Brandenburg Gate", "Neuschwanstein Castle", "Cologne Cathedral"),
            funFact = "Germany has over 20,000 castles and produces more than 1,500 different varieties of sausage."
        ),
        Country(
            code = "IT",
            name = "Italy",
            officialName = "Italian Republic",
            capital = "Rome",
            continent = "Europe",
            subregion = "Southern Europe",
            population = 58900000L,
            areaSqKm = 301340.0,
            flagEmoji = "🇮🇹",
            flagColors = listOf("Green", "White", "Red"),
            flagType = FlagStyle.VERTICAL_STRIPES_3,
            flagDescription = "The 'Tricolore' symbolises hope (green), faith (white), and charity (red), influenced by Napoleon's French flag design.",
            languages = listOf("Italian"),
            currency = "Euro (€)",
            landmarks = listOf("Colosseum", "Leaning Tower of Pisa", "Venice Canals"),
            funFact = "Italy contains two sovereign microstates completely surrounded by its borders: Vatican City and San Marino."
        ),
        Country(
            code = "GB",
            name = "United Kingdom",
            officialName = "United Kingdom of Great Britain and Northern Ireland",
            capital = "London",
            continent = "Europe",
            subregion = "Northern Europe",
            population = 67300000L,
            areaSqKm = 242495.0,
            flagEmoji = "🇬🇧",
            flagColors = listOf("Blue", "White", "Red"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "The Union Jack combines the crosses of St. George (England), St. Andrew (Scotland), and St. Patrick (Ireland).",
            languages = listOf("English"),
            currency = "Pound Sterling (£)",
            landmarks = listOf("Big Ben", "Stonehenge", "Tower Bridge"),
            funFact = "The BBC is the oldest national broadcasting organization in the world, founded in 1922."
        ),
        Country(
            code = "ES",
            name = "Spain",
            officialName = "Kingdom of Spain",
            capital = "Madrid",
            continent = "Europe",
            subregion = "Southern Europe",
            population = 47400000L,
            areaSqKm = 505990.0,
            flagEmoji = "🇪🇸",
            flagColors = listOf("Red", "Yellow"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_3,
            flagDescription = "Features two outer red stripes and a central yellow stripe twice the width, emblazoned with the Spanish Coat of Arms.",
            languages = listOf("Spanish"),
            currency = "Euro (€)",
            landmarks = listOf("Sagrada Família", "Alhambra Palace", "Park Güell"),
            funFact = "Spain produces over 40% of the world's olive oil, double that of Italy."
        ),
        Country(
            code = "GR",
            name = "Greece",
            officialName = "Hellenic Republic",
            capital = "Athens",
            continent = "Europe",
            subregion = "Southern Europe",
            population = 10400000L,
            areaSqKm = 131957.0,
            flagEmoji = "🇬🇷",
            flagColors = listOf("Blue", "White"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "Nine equal horizontal stripes of blue and white represent the 9 syllables of 'Freedom or Death', with a white cross for Greek Orthodoxy.",
            languages = listOf("Greek"),
            currency = "Euro (€)",
            landmarks = listOf("Parthenon", "Santorini Caldera", "Meteora Monasteries"),
            funFact = "Greece is widely considered the birthplace of Western civilization, democracy, Western philosophy, and the Olympic Games."
        ),
        Country(
            code = "SE",
            name = "Sweden",
            officialName = "Kingdom of Sweden",
            capital = "Stockholm",
            continent = "Europe",
            subregion = "Northern Europe",
            population = 10500000L,
            areaSqKm = 450295.0,
            flagEmoji = "🇸🇪",
            flagColors = listOf("Blue", "Yellow"),
            flagType = FlagStyle.CROSS_NORDIC,
            flagDescription = "A yellow Nordic cross on a blue field, inspired by the Danish Dannebrog and traditional royal colors.",
            languages = listOf("Swedish"),
            currency = "Swedish Krona (SEK)",
            landmarks = listOf("Vasa Museum", "ICEHOTEL Jukkasjärvi", "Gamla Stan"),
            funFact = "Sweden recycles 99% of its household waste, even importing rubbish from neighboring countries to fuel electricity plants."
        ),
        Country(
            code = "NO",
            name = "Norway",
            officialName = "Kingdom of Norway",
            capital = "Oslo",
            continent = "Europe",
            subregion = "Northern Europe",
            population = 5400000L,
            areaSqKm = 385207.0,
            flagEmoji = "🇳🇴",
            flagColors = listOf("Red", "White", "Blue"),
            flagType = FlagStyle.CROSS_NORDIC,
            flagDescription = "Red field with an off-center blue Nordic cross outlined in white, symbolizing liberty and Scandinavia.",
            languages = listOf("Norwegian"),
            currency = "Norwegian Krone (NOK)",
            landmarks = listOf("Geirangerfjord", "Preikestolen (Pulpit Rock)", "Northern Lights in Tromsø"),
            funFact = "Norway introduced salmon sushi to the Japanese in the 1980s."
        ),
        Country(
            code = "UA",
            name = "Ukraine",
            officialName = "Ukraine",
            capital = "Kyiv",
            continent = "Europe",
            subregion = "Eastern Europe",
            population = 38000000L,
            areaSqKm = 603628.0,
            flagEmoji = "🇺🇦",
            flagColors = listOf("Blue", "Yellow"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_2,
            flagDescription = "The top blue band represents the clear sky and peace, while the yellow bottom band symbolizes golden wheat fields.",
            languages = listOf("Ukrainian"),
            currency = "Ukrainian Hryvnia (₴)",
            landmarks = listOf("Kyiv Pechersk Lavra", "Saint Sophia Cathedral", "Tunnel of Love"),
            funFact = "Ukraine is home to the world's deepest metro station, Arsenalna in Kyiv, located 105.5 meters underground."
        ),

        // AMERICAS
        Country(
            code = "US",
            name = "United States",
            officialName = "United States of America",
            capital = "Washington, D.C.",
            continent = "Americas",
            subregion = "North America",
            population = 333000000L,
            areaSqKm = 9833520.0,
            flagEmoji = "🇺🇸",
            flagColors = listOf("Red", "White", "Blue"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "13 alternating red and white stripes represent the original 13 colonies, and 50 white stars on a blue canton represent the 50 states.",
            languages = listOf("English"),
            currency = "US Dollar ($)",
            landmarks = listOf("Statue of Liberty", "Grand Canyon", "Golden Gate Bridge"),
            funFact = "The US has no official national language specified in its constitution, although English is predominant."
        ),
        Country(
            code = "CA",
            name = "Canada",
            officialName = "Canada",
            capital = "Ottawa",
            continent = "Americas",
            subregion = "North America",
            population = 38900000L,
            areaSqKm = 9984670.0,
            flagEmoji = "🇨🇦",
            flagColors = listOf("Red", "White"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "The National Flag of Canada features a red 11-pointed maple leaf in the center of a white square flanked by red bars.",
            languages = listOf("English", "French"),
            currency = "Canadian Dollar (C$)",
            landmarks = listOf("CN Tower", "Banff National Park", "Niagara Falls"),
            funFact = "Canada has more lakes than all the rest of the world's countries combined, totaling over 2 million."
        ),
        Country(
            code = "MX",
            name = "Mexico",
            officialName = "United Mexican States",
            capital = "Mexico City",
            continent = "Americas",
            subregion = "North America",
            population = 126700000L,
            areaSqKm = 1964375.0,
            flagEmoji = "🇲🇽",
            flagColors = listOf("Green", "White", "Red"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Green for hope, white for unity, red for heroes' blood, with an Aztec coat of arms showing an eagle devouring a snake atop a cactus.",
            languages = listOf("Spanish"),
            currency = "Mexican Peso ($)",
            landmarks = listOf("Chichén Itzá", "Teotihuacan Pyramids", "Zócalo Square"),
            funFact = "Mexico City is sinking by about 10-20 centimeters per year because it was built on a dried-up lake bed."
        ),
        Country(
            code = "BR",
            name = "Brazil",
            officialName = "Federative Republic of Brazil",
            capital = "Brasília",
            continent = "Americas",
            subregion = "South America",
            population = 214300000L,
            areaSqKm = 8515767.0,
            flagEmoji = "🇧🇷",
            flagColors = listOf("Green", "Yellow", "Blue", "White"),
            flagType = FlagStyle.CENTER_CIRCLE,
            flagDescription = "Green represents the Amazon forest, yellow rhombus stands for gold wealth, blue globe depicts the night sky over Rio on Nov 15, 1889.",
            languages = listOf("Portuguese"),
            currency = "Brazilian Real (R$)",
            landmarks = listOf("Christ the Redeemer", "Iguazu Falls", "Amazon Rainforest"),
            funFact = "Brazil is the only country in the Americas where Portuguese is the official language."
        ),
        Country(
            code = "AR",
            name = "Argentina",
            officialName = "Argentine Republic",
            capital = "Buenos Aires",
            continent = "Americas",
            subregion = "South America",
            population = 45800000L,
            areaSqKm = 2780400.0,
            flagEmoji = "🇦🇷",
            flagColors = listOf("Light Blue", "White", "Yellow"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Three horizontal bands of light blue and white featuring the 'Sun of May' (Sol de Mayo) in the center.",
            languages = listOf("Spanish"),
            currency = "Argentine Peso ($)",
            landmarks = listOf("Perito Moreno Glacier", "Obelisco de Buenos Aires", "Iguazu Falls"),
            funFact = "Argentina invented tango dance and music in the late 19th century in the working-class port districts of Buenos Aires."
        ),
        Country(
            code = "CO",
            name = "Colombia",
            officialName = "Republic of Colombia",
            capital = "Bogotá",
            continent = "Americas",
            subregion = "South America",
            population = 51500000L,
            areaSqKm = 1141748.0,
            flagEmoji = "🇨🇴",
            flagColors = listOf("Yellow", "Blue", "Red"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_3,
            flagDescription = "Yellow double-width top stripe for agricultural wealth, blue for two oceans, and red for sovereignty and sacrifice.",
            languages = listOf("Spanish"),
            currency = "Colombian Peso ($)",
            landmarks = listOf("Salt Cathedral of Zipaquirá", "Cartagena Old Town", "Tayrona National Park"),
            funFact = "Colombia is the second most biodiverse country in the world, hosting 10% of the planet's species."
        ),
        Country(
            code = "CL",
            name = "Chile",
            officialName = "Republic of Chile",
            capital = "Santiago",
            continent = "Americas",
            subregion = "South America",
            population = 19500000L,
            areaSqKm = 756102.0,
            flagEmoji = "🇨🇱",
            flagColors = listOf("White", "Red", "Blue"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "Blue canton with a white five-pointed star representing progress and honor, above a red lower band for independence martyrs.",
            languages = listOf("Spanish"),
            currency = "Chilean Peso ($)",
            landmarks = listOf("Torres del Paine", "Easter Island Moai", "Atacama Desert"),
            funFact = "The Atacama Desert in Chile is the driest non-polar desert on Earth, with some weather stations receiving 0mm of rain for decades."
        ),

        // ASIA
        Country(
            code = "JP",
            name = "Japan",
            officialName = "Japan",
            capital = "Tokyo",
            continent = "Asia",
            subregion = "Eastern Asia",
            population = 125100000L,
            areaSqKm = 377975.0,
            flagEmoji = "🇯🇵",
            flagColors = listOf("White", "Red"),
            flagType = FlagStyle.CENTER_CIRCLE,
            flagDescription = "The 'Hinomaru' (Circle of the Sun) features a crimson red disc on a pure white field, embodying the Land of the Rising Sun.",
            languages = listOf("Japanese"),
            currency = "Japanese Yen (¥)",
            landmarks = listOf("Mount Fuji", "Fushimi Inari Shrine", "Tokyo Tower"),
            funFact = "Tokyo is the world's most populous metropolitan area, home to over 37 million residents."
        ),
        Country(
            code = "CN",
            name = "China",
            officialName = "People's Republic of China",
            capital = "Beijing",
            continent = "Asia",
            subregion = "Eastern Asia",
            population = 1412000000L,
            areaSqKm = 9596961.0,
            flagEmoji = "🇨🇳",
            flagColors = listOf("Red", "Yellow"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Red background represents the revolution, with a large golden star surrounded by four smaller stars symbolizing the unity of the Chinese people.",
            languages = listOf("Mandarin Chinese"),
            currency = "Chinese Yuan (¥ / 元)",
            landmarks = listOf("Great Wall of China", "Forbidden City", "Terracotta Army"),
            funFact = "The Great Wall of China is over 21,000 kilometers long when including all of its historical branches."
        ),
        Country(
            code = "IN",
            name = "India",
            officialName = "Republic of India",
            capital = "New Delhi",
            continent = "Asia",
            subregion = "Southern Asia",
            population = 1428000000L,
            areaSqKm = 3287263.0,
            flagEmoji = "🇮🇳",
            flagColors = listOf("Saffron", "White", "Green", "Navy"),
            flagType = FlagStyle.CENTER_CIRCLE,
            flagDescription = "Tiranga tricolor: Saffron for courage and sacrifice, white for peace and truth with 24-spoke Ashoka Chakra wheel, green for faith and chivalry.",
            languages = listOf("Hindi", "English"),
            currency = "Indian Rupee (₹)",
            landmarks = listOf("Taj Mahal", "Varanasi Ghats", "Amber Palace"),
            funFact = "India is the world's most populous democracy and the origin birthplace of four major world religions: Hinduism, Buddhism, Jainism, and Sikhism."
        ),
        Country(
            code = "KR",
            name = "South Korea",
            officialName = "Republic of Korea",
            capital = "Seoul",
            continent = "Asia",
            subregion = "Eastern Asia",
            population = 51700000L,
            areaSqKm = 100210.0,
            flagEmoji = "🇰🇷",
            flagColors = listOf("White", "Red", "Blue", "Black"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "White field stands for peace, central Taegeuk red/blue circle symbolizes universal balance (Yin and Yang), surrounded by 4 black trigrams.",
            languages = listOf("Korean"),
            currency = "South Korean Won (₩)",
            landmarks = listOf("Gyeongbokgung Palace", "Jeju Island", "N Seoul Tower"),
            funFact = "South Korea has the highest internet connection speeds in the world and pioneered K-pop and esports global culture."
        ),
        Country(
            code = "VN",
            name = "Vietnam",
            officialName = "Socialist Republic of Vietnam",
            capital = "Hanoi",
            continent = "Asia",
            subregion = "South-Eastern Asia",
            population = 98000000L,
            areaSqKm = 331212.0,
            flagEmoji = "🇻🇳",
            flagColors = listOf("Red", "Yellow"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Red field symbolizes revolution and bloodshed, with a central five-pointed yellow star representing workers, peasants, soldiers, intellectuals, and traders.",
            languages = listOf("Vietnamese"),
            currency = "Vietnamese Đồng (₫)",
            landmarks = listOf("Ha Long Bay", "Hoi An Ancient Town", "Cu Chi Tunnels"),
            funFact = "Vietnam is the world's second-largest producer and exporter of coffee, famous for iced egg coffee (Cà phê trứng)."
        ),
        Country(
            code = "TH",
            name = "Thailand",
            officialName = "Kingdom of Thailand",
            capital = "Bangkok",
            continent = "Asia",
            subregion = "South-Eastern Asia",
            population = 71600000L,
            areaSqKm = 513120.0,
            flagEmoji = "🇹🇭",
            flagColors = listOf("Red", "White", "Blue"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_3,
            flagDescription = "The Trairanga flag has 5 horizontal stripes: Red for the land and people, white for religion, and central blue for the monarchy.",
            languages = listOf("Thai"),
            currency = "Thai Baht (฿)",
            landmarks = listOf("Grand Palace Bangkok", "Wat Arun", "Phi Phi Islands"),
            funFact = "Bangkok's full ceremonial ceremonial ritual name is 168 letters long, making it the longest place name in the world."
        ),
        Country(
            code = "SA",
            name = "Saudi Arabia",
            officialName = "Kingdom of Saudi Arabia",
            capital = "Riyadh",
            continent = "Asia",
            subregion = "Western Asia",
            population = 36400000L,
            areaSqKm = 2149690.0,
            flagEmoji = "🇸🇦",
            flagColors = listOf("Green", "White"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Green field featuring white Arabic calligraphy of the Shahada (Islamic creed) and a horizontal sword pointing left.",
            languages = listOf("Arabic"),
            currency = "Saudi Riyal (﷼)",
            landmarks = listOf("Al-Ula & Hegra", "Masmak Fortress", "Kingdom Centre Tower"),
            funFact = "Saudi Arabia is the largest country in the world without a single permanent natural river flowing through it."
        ),

        // AFRICA
        Country(
            code = "EG",
            name = "Egypt",
            officialName = "Arab Republic of Egypt",
            capital = "Cairo",
            continent = "Africa",
            subregion = "Northern Africa",
            population = 109000000L,
            areaSqKm = 1002450.0,
            flagEmoji = "🇪🇬",
            flagColors = listOf("Red", "White", "Black", "Gold"),
            flagType = FlagStyle.HORIZONTAL_STRIPES_3,
            flagDescription = "Three horizontal bands: Red for the period before revolution, white for peaceful transition, black for oppression end, featuring the Golden Eagle of Saladin.",
            languages = listOf("Arabic"),
            currency = "Egyptian Pound (E£)",
            landmarks = listOf("Great Pyramids of Giza", "Sphinx", "Luxor Temple"),
            funFact = "The Great Pyramid of Giza was the tallest man-made structure in the world for over 3,800 years."
        ),
        Country(
            code = "KE",
            name = "Kenya",
            officialName = "Republic of Kenya",
            capital = "Nairobi",
            continent = "Africa",
            subregion = "Eastern Africa",
            population = 54000000L,
            areaSqKm = 580367.0,
            flagEmoji = "🇰🇪",
            flagColors = listOf("Black", "Red", "Green", "White"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Black for native population, red for freedom struggle, green for natural resources, with white fimbriations and a traditional Maasai warrior shield and spears.",
            languages = listOf("Swahili", "English"),
            currency = "Kenyan Shilling (KSh)",
            landmarks = listOf("Maasai Mara National Reserve", "Mount Kenya", "Amboseli National Park"),
            funFact = "Kenya is home to the Great Wildebeest Migration, where over 1.5 million animals cross the Mara River every year."
        ),
        Country(
            code = "ZA",
            name = "South Africa",
            officialName = "Republic of South Africa",
            capital = "Pretoria / Cape Town / Bloemfontein",
            continent = "Africa",
            subregion = "Southern Africa",
            population = 59800000L,
            areaSqKm = 1221037.0,
            flagEmoji = "🇿🇦",
            flagColors = listOf("Black", "Gold", "Green", "White", "Red", "Blue"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "A horizontal Y-shape band symbolizes the convergence of diverse historic elements into a unified national rainbow nation.",
            languages = listOf("Zulu", "Xhosa", "Afrikaans", "English", "Sotho + 7 more"),
            currency = "South African Rand (R)",
            landmarks = listOf("Table Mountain", "Kruger National Park", "Robben Island"),
            funFact = "South Africa is the only country in the world with three official capital cities: Pretoria (Executive), Cape Town (Legislative), and Bloemfontein (Judicial)."
        ),
        Country(
            code = "NG",
            name = "Nigeria",
            officialName = "Federal Republic of Nigeria",
            capital = "Abuja",
            continent = "Africa",
            subregion = "Western Africa",
            population = 218500000L,
            areaSqKm = 923768.0,
            flagEmoji = "🇳🇬",
            flagColors = listOf("Green", "White"),
            flagType = FlagStyle.VERTICAL_STRIPES_3,
            flagDescription = "Three vertical bands: Green represents agriculture and natural wealth, while white represents peace and unity.",
            languages = listOf("English", "Hausa", "Yoruba", "Igbo"),
            currency = "Nigerian Naira (₦)",
            landmarks = listOf("Zuma Rock", "Lekki Conservation Centre", "Osun-Osogbo Sacred Grove"),
            funFact = "Nollywood, Nigeria's film industry, is the second largest movie producer in the world by volume, producing over 2,500 films a year."
        ),
        Country(
            code = "MA",
            name = "Morocco",
            officialName = "Kingdom of Morocco",
            capital = "Rabat",
            continent = "Africa",
            subregion = "Northern Africa",
            population = 37300000L,
            areaSqKm = 446550.0,
            flagEmoji = "🇲🇦",
            flagColors = listOf("Red", "Green"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Deep red background symbolizing the Alaouite dynasty, featuring a central green five-pointed star (Seal of Solomon).",
            languages = listOf("Arabic", "Berber", "French"),
            currency = "Moroccan Dirham (MAD)",
            landmarks = listOf("Chefchaouen Blue City", "Marrakech Medina", "Hassan II Mosque"),
            funFact = "The University of al-Qarawiyyin in Fez, Morocco was founded in 859 AD and is recognized by UNESCO as the world's oldest continually operating university."
        ),
        Country(
            code = "TZ",
            name = "Tanzania",
            officialName = "United Republic of Tanzania",
            capital = "Dodoma",
            continent = "Africa",
            subregion = "Eastern Africa",
            population = 63600000L,
            areaSqKm = 947303.0,
            flagEmoji = "🇹🇿",
            flagColors = listOf("Green", "Yellow", "Black", "Blue"),
            flagType = FlagStyle.COMPLEX_EMBLEM,
            flagDescription = "Green triangle for natural vegetation, yellow stripes for mineral deposits, black diagonal band for people, and blue triangle for Indian Ocean lakes.",
            languages = listOf("Swahili", "English"),
            currency = "Tanzanian Shilling (TSh)",
            landmarks = listOf("Mount Kilimanjaro", "Serengeti National Park", "Zanzibar Beaches"),
            funFact = "Tanzania contains Mount Kilimanjaro, Africa's highest mountain peak at 5,895 meters above sea level."
        ),

        // OCEANIA
        Country(
            code = "AU",
            name = "Australia",
            officialName = "Commonwealth of Australia",
            capital = "Canberra",
            continent = "Oceania",
            subregion = "Australia and New Zealand",
            population = 26000000L,
            areaSqKm = 7692024.0,
            flagEmoji = "🇦🇺",
            flagColors = listOf("Blue", "White", "Red"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "Blue ensign with Union Jack canton, large 7-pointed Commonwealth Star beneath, and 5 stars of the Southern Cross constellation on the fly.",
            languages = listOf("English"),
            currency = "Australian Dollar (A$)",
            landmarks = listOf("Sydney Opera House", "Great Barrier Reef", "Uluru (Ayers Rock)"),
            funFact = "Australia is home to over 80% of unique mammals, reptiles, and plants found nowhere else on Earth."
        ),
        Country(
            code = "NZ",
            name = "New Zealand",
            officialName = "New Zealand",
            capital = "Wellington",
            continent = "Oceania",
            subregion = "Australia and New Zealand",
            population = 5120000L,
            areaSqKm = 268021.0,
            flagEmoji = "🇳🇿",
            flagColors = listOf("Blue", "Red", "White"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "Blue ensign featuring Union Jack canton and four red 5-pointed stars with white borders representing the Southern Cross constellation.",
            languages = listOf("English", "Māori"),
            currency = "New Zealand Dollar (NZ$)",
            landmarks = listOf("Milford Sound", "Hobbiton Movie Set", "Aoraki / Mount Cook"),
            funFact = "New Zealand was the first country in the world to grant all women the right to vote in parliamentary elections in 1893."
        ),
        Country(
            code = "FJ",
            name = "Fiji",
            officialName = "Republic of Fiji",
            capital = "Suva",
            continent = "Oceania",
            subregion = "Melanesia",
            population = 926000L,
            areaSqKm = 18274.0,
            flagEmoji = "🇫🇯",
            flagColors = listOf("Light Blue", "Red", "White", "Yellow", "Green"),
            flagType = FlagStyle.CANTON_STARS,
            flagDescription = "Light blue field with Union Jack canton and Fiji national coat of arms depicting sugarcane, coconut palm, banana, and peace dove.",
            languages = listOf("English", "Fijian", "Fiji Hindi"),
            currency = "Fijian Dollar (FJ$)",
            landmarks = listOf("Mamanuca Islands", "Sri Siva Subramaniya Temple", "Bouma National Heritage Park"),
            funFact = "Fiji consists of an archipelago of more than 330 islands, of which only about 110 are permanently inhabited."
        ),

        // ANTARCTICA
        Country(
            code = "AQ",
            name = "Antarctica",
            officialName = "Antarctic Treaty Territory",
            capital = "McMurdo Station (De Facto)",
            continent = "Antarctica",
            subregion = "Antarctica",
            population = 1100L, // Seasonal scientists
            areaSqKm = 14200000.0,
            flagEmoji = "🇦🇶",
            flagColors = listOf("Blue", "White"),
            flagType = FlagStyle.CENTER_CIRCLE,
            flagDescription = "Graham Bartram's true flag design shows a plain white silhouette map of the Antarctic continent centered on a sky blue background.",
            languages = listOf("English", "Russian", "Spanish", "French"),
            currency = "Antarctic Dollar / USD",
            landmarks = listOf("South Pole Station", "Blood Falls", "Mount Erebus Volcano"),
            funFact = "Antarctica holds 70% of the world's fresh water and 90% of the world's ice, making it the coldest, windiest, and driest continent on Earth."
        )
    )

    fun getCountryByCode(code: String): Country? {
        return allCountries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }

    fun filterCountries(
        query: String,
        continent: String,
        sortBy: SortOption
    ): List<Country> {
        return allCountries.filter { country ->
            val matchesQuery = query.isBlank() ||
                    country.name.contains(query, ignoreCase = true) ||
                    country.capital.contains(query, ignoreCase = true) ||
                    country.officialName.contains(query, ignoreCase = true)
            val matchesContinent = continent == "All" || country.continent.equals(continent, ignoreCase = true)
            matchesQuery && matchesContinent
        }.let { list ->
            when (sortBy) {
                SortOption.NAME -> list.sortedBy { it.name }
                SortOption.POPULATION -> list.sortedByDescending { it.population }
                SortOption.AREA -> list.sortedByDescending { it.areaSqKm }
                SortOption.CONTINENT -> list.sortedBy { it.continent }
            }
        }
    }

    val userProgress: Flow<List<UserProgressEntity>> = progressDao.getAllProgress()

    suspend fun toggleFavorite(code: String, currentProgress: UserProgressEntity?) {
        val newFavorite = !(currentProgress?.isFavorite ?: false)
        val updated = (currentProgress ?: UserProgressEntity(countryCode = code)).copy(
            isFavorite = newFavorite
        )
        progressDao.upsertProgress(updated)
    }

    suspend fun recordReview(code: String, isCorrect: Boolean) {
        val current = progressDao.getAllProgress() // or single check
        // Progress DAO helper
    }

    suspend fun saveQuizScore(mode: String, score: Int, total: Int, continent: String) {
        progressDao.insertQuizScore(
            com.example.data.local.QuizScoreEntity(
                mode = mode,
                score = score,
                total = total,
                continentFilter = continent
            )
        )
    }
}

enum class SortOption {
    NAME, POPULATION, AREA, CONTINENT
}
