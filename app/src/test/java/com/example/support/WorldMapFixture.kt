package com.example.support

import com.example.data.model.WorldMapData

/**
 * A small map data set in the asset's format, for tests that must not depend on `world.json`: a wide country on
 * the equator (AA, with a closed ring and no frame), one far from it (BB), one astride the antimeridian in two rings
 * (XX, its box in the shifted frame), a one-square microstate (PP), a country with a far-flung territory (FF: the
 * frame leaves it out), a speck of a country (SS), a polar one (AQ), a host (HH) in two rings that overlap, as
 * simplified rings can, with a hole that its enclave (EE) fills, and one ring of background land (no country) in the
 * North Pacific.
 */
object WorldMapFixture {
    const val JSON = """{"format":2,"source":"test","countries":{
        "AA":{"polys":[[[-30,-10],[30,-10],[30,10],[-30,10],[-30,-10]]],"bbox":[-30,-10,30,10],"label":[0,0],"capital":[10,5],"point":false},
        "BB":{"polys":[[[60,20],[80,20],[80,40],[60,40]]],"bbox":[60,20,80,40],"label":[70,30],"capital":null,"point":false},
        "XX":{"polys":[[[170,-10],[180,-10],[180,10],[170,10]],[[-180,-10],[-170,-10],[-170,10],[-180,10]]],"bbox":[170,-10,190,10],"frame":[170,-10,190,10],"label":[180,0],"capital":[178,0],"point":false},
        "PP":{"polys":[[[12.4,41.9],[12.5,41.9],[12.5,42.0],[12.4,42.0]]],"bbox":[12.4,41.9,12.5,42.0],"frame":[12.4,41.9,12.5,42.0],"label":[12.45,41.95],"capital":[12.45,41.95],"point":true},
        "FF":{"polys":[[[-5,42],[8,42],[8,51],[-5,51]],[[-54,2],[-52,2],[-52,5],[-54,5]]],"bbox":[-54,2,8,51],"frame":[-5,42,8,51],"label":[2,46],"capital":[2.35,48.85],"point":false},
        "SS":{"polys":[[[100,0],[101,0],[101,1],[100,1]]],"bbox":[100,0,101,1],"frame":[100,0,101,1],"label":[100.5,0.5],"capital":null,"point":false},
        "AQ":{"polys":[[[-180,-90],[180,-90],[180,-62],[-180,-62]]],"bbox":[-180,-90,180,-62],"frame":[-180,-90,180,-62],"label":[0,-80],"capital":null,"point":false},
        "HH":{"polys":[[[20,-40],[40,-40],[40,-20],[20,-20]],[[16,-38],[24,-38],[24,-22],[16,-22]]],"holes":[[[26,-34],[34,-34],[34,-26],[26,-26]]],"bbox":[16,-40,40,-20],"frame":[16,-40,40,-20],"label":[23,-23],"capital":null,"point":false},
        "EE":{"polys":[[[26,-34],[34,-34],[34,-26],[26,-26]]],"holes":[],"bbox":[26,-34,34,-26],"frame":[26,-34,34,-26],"label":[30,-30],"capital":null,"point":false}
    },"background":[[[-170,30],[-150,30],[-150,50],[-170,50]]]}"""

    val data: WorldMapData by lazy { WorldMapData.parse(JSON) }
}
