package com.example.data.model

/**
 * The countries of Americas as the UN M49 geoscheme defines the region.
 * Entries are alphabetical by name; tools/gen-entries.py inserts new ones in place.
 */
object AmericasCatalog {
    val countries: List<Country> = listOf(
        Country(
            code = "AR",
            name = "Argentina",
            officialName = "Argentine Republic",
            capital = "Buenos Aires",
            continent = "Americas",
            subregion = "South America",
            population = 45700000L,
            areaSqKm = 2780400.0,
            flagEmoji = "🇦🇷",
            flagColors = listOf("Light Blue", "White", "Yellow"),
            flagDescription = "Manuel Belgrano made the first flag in 1812, with light blue and white to match the badge patriot soldiers wore. Records are unclear why the badge had those colors. The golden sun was added in 1818. It copies the sun on the first Argentine coin (1813), and its 32 rays alternate straight and wavy. It is called the Sun of May for the revolution of May 1810, when, the story goes, the sun shone through the clouds.",
            languages = listOf("Spanish"),
            currency = "Argentine Peso ($)",
            landmarks = listOf("Perito Moreno Glacier", "Obelisk of Buenos Aires", "Iguazu Falls"),
            funFact = "Tango took shape in the late 1800s in the port districts of Buenos Aires and Montevideo, and Argentina and Uruguay share its UNESCO heritage listing."
        ),
        Country(
            code = "BR",
            name = "Brazil",
            officialName = "Federative Republic of Brazil",
            capital = "Brasília",
            continent = "Americas",
            subregion = "South America",
            population = 212000000L,
            areaSqKm = 8510346.0,
            flagEmoji = "🇧🇷",
            flagColors = listOf("Green", "Yellow", "Blue", "White"),
            flagDescription = "The green background and yellow diamond come from the empire's flag, kept in 1889. They are linked to the royal houses of Emperor Pedro I (green) and his wife (yellow), or read as forests and gold. The blue circle shows the sky over Rio de Janeiro on November 15, 1889, when the republic began. Its 27 stars stand for 26 states and the Federal District. The white band says Ordem e Progresso (Order and Progress), from a motto by Auguste Comte.",
            languages = listOf("Portuguese"),
            currency = "Brazilian Real (R$)",
            landmarks = listOf("Christ the Redeemer", "Iguazu Falls", "Amazon Rainforest"),
            funFact = "Brazil is the only country in the Americas where Portuguese is the official language."
        ),
        Country(
            code = "CA",
            name = "Canada",
            officialName = "Canada",
            capital = "Ottawa",
            continent = "Americas",
            subregion = "Northern America",
            population = 39700000L,
            areaSqKm = 9984670.0,
            flagEmoji = "🇨🇦",
            flagColors = listOf("Red", "White"),
            flagDescription = "The red maple leaf has long been a Canadian symbol and is on the coat of arms from 1921. Red and white come from that coat of arms; red is often traced to England and white to France. The 11 points have no special meaning. The two red bars beside a white square were inspired by a military college's flag. It became the national flag in 1965. Before that, many Canadians used a flag with the British Union Jack in a corner.",
            languages = listOf("English", "French"),
            currency = "Canadian Dollar (C$)",
            landmarks = listOf("CN Tower", "Banff National Park", "Niagara Falls"),
            funFact = "Canada has more lakes than any other country, and the longest coastline in the world, touching three oceans."
        ),
        Country(
            code = "CL",
            name = "Chile",
            officialName = "Republic of Chile",
            capital = "Santiago",
            continent = "Americas",
            subregion = "South America",
            population = 19800000L,
            areaSqKm = 756102.0,
            flagEmoji = "🇨🇱",
            flagColors = listOf("White", "Red", "Blue"),
            flagDescription = "The flag was made official in 1817 under Bernardo O'Higgins. Its blue, white and red come from a flag used earlier that year, where red replaced the yellow of the 1812 flag. The official rules give no meaning for the colors, star or blue square. Blue is often said to be the sky, white the snowy Andes, red the blood shed for freedom. The star is seen as a guide to progress and honor, and is traditionally linked to the Mapuche morning star.",
            languages = listOf("Spanish"),
            currency = "Chilean Peso ($)",
            landmarks = listOf("Torres del Paine", "Easter Island Moai", "Atacama Desert"),
            funFact = "The Atacama Desert in northern Chile is the driest non-polar desert on Earth, and some parts have gone years in a row without any rain."
        ),
        Country(
            code = "CO",
            name = "Colombia",
            officialName = "Republic of Colombia",
            capital = "Bogotá",
            continent = "Americas",
            subregion = "South America",
            population = 52900000L,
            areaSqKm = 1138910.0,
            flagEmoji = "🇨🇴",
            flagColors = listOf("Yellow", "Blue", "Red"),
            flagDescription = "Francisco de Miranda first flew these yellow, blue and red colors in 1806. Many stories try to explain his choice, but none is certain. Simón Bolívar's armies made the flag famous, and in 1819 Gran Colombia kept it. The government says yellow stands for the country's riches and justice, blue for the sky, the seas on its coasts and its rivers, and red for the blood spilled for independence. An 1861 decree made yellow the top half.",
            languages = listOf("Spanish"),
            currency = "Colombian Peso ($)",
            landmarks = listOf("Salt Cathedral of Zipaquirá", "Cartagena Old Town", "Tayrona National Park"),
            funFact = "Colombia is one of the world's megadiverse countries: it covers under 1% of Earth's land but holds close to 10% of the planet's biodiversity."
        ),
        Country(
            code = "MX",
            name = "Mexico",
            officialName = "United Mexican States",
            capital = "Mexico City",
            continent = "Americas",
            subregion = "Central America",
            population = 131000000L,
            areaSqKm = 1964375.0,
            flagEmoji = "🇲🇽",
            flagColors = listOf("Green", "White", "Red"),
            flagDescription = "The colors come from the flag of the army that won independence in 1821. They were linked to the army's promises of independence (green), religion (white) and union (red). Later they were often read as hope, unity and heroes' blood, but the law gives no official meaning. The eagle eating a snake on a cactus comes from an Aztec legend about where to build their capital. Oak and laurel branches are traditionally linked to strength and victory.",
            languages = listOf("Spanish"),
            currency = "Mexican Peso ($)",
            landmarks = listOf("Chichén Itzá", "Teotihuacan Pyramids", "Zócalo Square"),
            funFact = "Mexico City sits on the drained bed of Lake Texcoco, and pumping groundwater has made parts of it sink by several meters over the past century."
        ),
        Country(
            code = "US",
            name = "United States",
            officialName = "United States of America",
            capital = "Washington, D.C.",
            continent = "Americas",
            subregion = "Northern America",
            population = 345400000L,
            areaSqKm = 9525067.0,
            flagEmoji = "🇺🇸",
            flagColors = listOf("Red", "White", "Blue"),
            flagDescription = "The 13 red and white stripes stand for the 13 original states. The blue rectangle sits where the British Union Jack was on an earlier flag. In 1777 Congress put white stars there as a new constellation, meaning a new nation. Each of the 50 states has one star, a design from 1960. The colors have no official meaning, but red is often linked to courage, white to purity and blue to justice.",
            languages = listOf("English"),
            currency = "US Dollar ($)",
            landmarks = listOf("Statue of Liberty", "Grand Canyon", "Golden Gate Bridge"),
            funFact = "Alaska's Aleutian Islands cross the 180th meridian, so a small part of the United States lies in the Eastern Hemisphere."
        )
    )
}
