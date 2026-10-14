package com.example.data.model

/** Tell-apart tips, part 1: the curated look-alike groups from Chad/Romania to Somalia/Micronesia. */
internal val TELL_APART_TIPS_1: Map<String, String> = mapOf(
    // Blue, yellow and red vertical bands
    "AD MD" to "Andorra carries a small framed shield on the yellow band; Moldova has a large brown eagle that spreads onto the blue and red.",
    "AD RO" to "Andorra puts its coat of arms on the yellow band; Romania's blue, yellow and red bands are plain.",
    "AD TD" to "Andorra has a coat of arms on the middle band; Chad's blue, yellow and red bands carry nothing at all.",
    "MD RO" to "Moldova has a brown eagle holding a shield on the yellow band; Romania's bands are plain.",
    "MD TD" to "Moldova shows an eagle and shield on its yellow band, while Chad's bands are plain and its blue is darker.",
    "RO TD" to "Chad's blue is a deep indigo, Romania's a lighter cobalt; otherwise the flags are the same.",

    // Red over white
    "ID MC" to "In the app Indonesia and Monaco look the same; Monaco's real flag is nearly square, Indonesia's is wider, and Monaco's red is a shade brighter.",
    "ID PL" to "Indonesia is red over white; Poland is the other way up, white over red.",
    "MC PL" to "Monaco is red over white; Poland turns it over, white above red.",

    // Green, white and orange or red bands
    "CI IE" to "Ivory Coast starts with orange at the hoist and ends in green; Ireland starts with green and ends in orange.",
    "CI IT" to "Ivory Coast's hoist band is orange and its last band green; Italy runs green, white, red.",
    "CI MX" to "Ivory Coast is plain orange, white and green; Mexico is green, white and red with an eagle on the white band.",
    "IE IT" to "Ireland ends in an orange band, Italy in a red one; both start with green at the hoist.",
    "IE MX" to "Mexico carries an eagle on its white band and ends in red; Ireland's bands are plain and end in orange.",
    "IT MX" to "Mexico has an eagle on a cactus in the centre of its white band; Italy's white band is empty, and Mexico's green is darker.",

    // Union Jack and Southern Cross
    "AU NZ" to "New Zealand has four red stars edged in white; Australia has six white stars, including a large seven-pointed one under the Union Jack.",

    // Nordic crosses
    "DK FI" to "Denmark is a white cross on red; Finland is a blue cross on white.",
    "DK IS" to "Denmark's cross is white on a red field; Iceland's is red edged in white on a blue field.",
    "DK NO" to "Both have red fields, but Norway's cross is blue with a white border while Denmark's is plain white.",
    "DK SE" to "Denmark is a white cross on red; Sweden is a yellow cross on blue.",
    "FI IS" to "Finland is a blue cross on a white field; Iceland reverses it to a blue field with a red cross outlined in white.",
    "FI NO" to "Finland is a plain blue cross on white; Norway is a blue cross inside a white border on red.",
    "FI SE" to "Finland's cross is blue on white; Sweden's is yellow on blue.",
    "IS NO" to "Iceland has a blue field with a red cross; Norway has a red field with a blue cross, each cross edged in white.",
    "IS SE" to "Both fields are blue, but Iceland's cross is red with a white border and Sweden's is plain yellow.",
    "NO SE" to "Norway is a white-edged blue cross on red; Sweden is a plain yellow cross on blue.",

    // Red, white and blue bands
    "FR LU" to "France has vertical blue, white and red bands; Luxembourg's red, white and light blue bands run horizontally.",
    "FR NL" to "France's bands are vertical with blue at the hoist; the Netherlands stacks red, white and blue horizontally.",
    "FR RU" to "France's blue, white and red run as vertical bands; Russia's white, blue and red are horizontal.",
    "LU NL" to "Luxembourg's bottom band is a light sky blue; the Netherlands uses a dark navy blue.",
    "LU RU" to "Luxembourg is red at the top and light blue at the bottom; Russia is white at the top and red at the bottom.",
    "NL RU" to "The Netherlands puts red on top and blue at the bottom; Russia puts white on top with red at the bottom.",

    // Slavic red, white and blue
    "HR RS" to "Croatia is red, white and blue with a chequered shield in the centre; Serbia is red, blue and white with a crowned eagle shield.",
    "HR RU" to "Croatia has a chequered shield in the centre of its red, white and blue; Russia's white, blue and red bands carry no emblem.",
    "HR SI" to "Croatia is red on top with a chequered shield in the centre; Slovenia is white on top with a small shield near the top hoist corner.",
    "HR SK" to "Croatia starts with red and has a chequered shield in the middle; Slovakia starts with white and has a double-cross shield towards the hoist.",
    "RS RU" to "Serbia is red, blue and white with a crowned eagle shield towards the hoist; Russia is white, blue and red with nothing on it.",
    "RS SI" to "Serbia has red at the top and a crowned double-headed eagle; Slovenia has white at the top and a shield with three peaks and three stars.",
    "RS SK" to "Serbia's bands run red, blue, white under a crowned eagle shield; Slovakia's run white, blue, red beside a red shield with a double cross.",
    "RU SI" to "Slovenia adds a small blue shield with white peaks and three stars at the top hoist; Russia's white, blue and red bands are plain.",
    "RU SK" to "Slovakia carries a red shield with a white double cross towards the hoist; Russia's white, blue and red bands have no emblem.",
    "SI SK" to "Slovenia's shield is blue with white peaks and three gold stars, up in the top corner; Slovakia's is red with a white double cross and sits lower.",

    // Gran Colombia yellow, blue and red
    "CO EC" to "Ecuador has a large coat of arms with a condor in the centre; Colombia's yellow, blue and red bands are plain.",
    "CO VE" to "Colombia's yellow takes up the top half and has no stars; Venezuela's bands are equal with an arc of eight white stars on the blue.",
    "EC VE" to "Ecuador has a big coat of arms and a yellow band twice as deep as the others; Venezuela has an arc of eight white stars on equal bands.",

    // Red, yellow and green
    "CM GN" to "Cameroon has a yellow star on its red middle band; Guinea starts with red at the hoist and has no star.",
    "CM ML" to "Cameroon's middle band is red with a yellow star; Mali's middle band is yellow and the flag has no star.",
    "CM SN" to "Both start with green, but Cameroon's star is yellow on a red middle band and Senegal's is green on a yellow middle band.",
    "GN ML" to "Guinea runs red, yellow, green from the hoist; Mali runs the same colours the other way, green, yellow, red.",
    "GN SN" to "Guinea's hoist band is red with no star; Senegal's is green and its yellow band carries a green star.",
    "ML SN" to "Senegal has a green star in the centre of its yellow band; Mali is the same green, yellow and red with no star.",

    // Central American and Argentine blue, white and blue
    "AR GT" to "Argentina's blue, white and blue bands run horizontally with a sun; Guatemala's run vertically with a quetzal and wreath.",
    "AR HN" to "Argentina has a yellow sun on its white band and a paler blue; Honduras has five blue stars on its white band.",
    "AR NI" to "Argentina shows a yellow sun with a face; Nicaragua shows a triangle emblem ringed with text on a stronger blue.",
    "AR SV" to "Argentina's blue is a pale sky blue and its emblem a sun; El Salvador's blue is deeper and its emblem a triangle in a wreath.",
    "AR UY" to "Both have a sun, but Argentina's sits on a single white band between two blue ones and Uruguay's sits in the corner of nine stripes.",
    "GT HN" to "Guatemala's bands are vertical with a quetzal and wreath in the centre; Honduras has horizontal bands with five blue stars.",
    "GT NI" to "Guatemala's blue bands run vertically; Nicaragua's run horizontally around a triangle emblem.",
    "GT SV" to "Guatemala's bands are vertical with a quetzal in the centre; El Salvador's are horizontal with a triangle in a wreath.",
    "GT UY" to "Guatemala has three vertical bands with a quetzal; Uruguay has nine horizontal stripes with a sun in the corner.",
    "HN NI" to "Honduras has five blue stars on its white band; Nicaragua has a triangle emblem ringed by text instead.",
    "HN SV" to "Honduras has five plain blue stars in an X on the white band; El Salvador has a coat of arms with a triangle in a wreath.",
    "HN UY" to "Honduras is three bands with five blue stars; Uruguay is nine thin stripes with a sun in the corner.",
    "NI SV" to "Nicaragua's triangle has only a ring of text around it; El Salvador's triangle is enclosed by a green laurel wreath.",
    "NI UY" to "Nicaragua has three bands with a triangle emblem; Uruguay has nine stripes and a sun in the top hoist corner.",
    "SV UY" to "El Salvador has three bands with a wreathed coat of arms; Uruguay has nine stripes with a sun in the corner.",

    // White with a serrated edge
    "BH QA" to "Bahrain is bright red with five white points along the hoist; Qatar is maroon with nine.",

    // Red, white and black bands
    "EG IQ" to "Egypt has a gold eagle on its white band; Iraq has green Arabic script instead.",
    "EG YE" to "Egypt carries a gold eagle in the centre of its white band; Yemen's red, white and black bands are empty.",
    "IQ YE" to "Iraq has green Arabic writing on its white band; Yemen's bands are plain.",

    // Red with a white crescent
    "TN TR" to "Tunisia puts a red crescent and star inside a white disc; Turkey's crescent and star are white straight on the red.",

    // Blue over red with an emblem
    "HT LI" to "Haiti has a coat of arms on a white panel where the blue meets the red; Liechtenstein has a gold crown in the top hoist corner.",

    // Light blue with white stars
    "FM SO" to "Micronesia has four small white stars in a diamond; Somalia has one large white star in the centre.",
)
