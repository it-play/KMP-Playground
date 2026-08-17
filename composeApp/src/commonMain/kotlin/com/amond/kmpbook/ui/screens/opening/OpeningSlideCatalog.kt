package com.amond.kmpbook.ui.screens.opening

import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.opening_slide_nyse_1963
import kmpbook.composeapp.generated.resources.opening_slide_nasdaq_2021
import kmpbook.composeapp.generated.resources.opening_slide_yeouido_1984
import kmpbook.composeapp.generated.resources.opening_slide_004
import kmpbook.composeapp.generated.resources.opening_slide_005
import kmpbook.composeapp.generated.resources.opening_slide_006
import kmpbook.composeapp.generated.resources.opening_slide_007
import kmpbook.composeapp.generated.resources.opening_slide_008
import kmpbook.composeapp.generated.resources.opening_slide_009
import kmpbook.composeapp.generated.resources.opening_slide_010
import kmpbook.composeapp.generated.resources.opening_slide_011
import kmpbook.composeapp.generated.resources.opening_slide_012
import kmpbook.composeapp.generated.resources.opening_slide_013
import kmpbook.composeapp.generated.resources.opening_slide_014
import kmpbook.composeapp.generated.resources.opening_slide_015
import kmpbook.composeapp.generated.resources.opening_slide_016
import kmpbook.composeapp.generated.resources.opening_slide_017
import kmpbook.composeapp.generated.resources.opening_slide_018
import kmpbook.composeapp.generated.resources.opening_slide_019
import kmpbook.composeapp.generated.resources.opening_slide_020
import kmpbook.composeapp.generated.resources.opening_slide_021
import kmpbook.composeapp.generated.resources.opening_slide_022
import kmpbook.composeapp.generated.resources.opening_slide_023
import kmpbook.composeapp.generated.resources.opening_slide_024
import kmpbook.composeapp.generated.resources.opening_slide_025
import kmpbook.composeapp.generated.resources.opening_slide_026
import kmpbook.composeapp.generated.resources.opening_slide_027
import kmpbook.composeapp.generated.resources.opening_slide_028
import kmpbook.composeapp.generated.resources.opening_slide_029
import kmpbook.composeapp.generated.resources.opening_slide_030
import kmpbook.composeapp.generated.resources.opening_slide_031
import kmpbook.composeapp.generated.resources.opening_slide_032
import kmpbook.composeapp.generated.resources.opening_slide_033
import kmpbook.composeapp.generated.resources.opening_slide_034
import kmpbook.composeapp.generated.resources.opening_slide_035
import kmpbook.composeapp.generated.resources.opening_slide_036
import kmpbook.composeapp.generated.resources.opening_slide_037
import kmpbook.composeapp.generated.resources.opening_slide_038
import kmpbook.composeapp.generated.resources.opening_slide_039
import kmpbook.composeapp.generated.resources.opening_slide_040
import kmpbook.composeapp.generated.resources.opening_slide_041
import kmpbook.composeapp.generated.resources.opening_slide_042
import kmpbook.composeapp.generated.resources.opening_slide_043
import kmpbook.composeapp.generated.resources.opening_slide_044
import kmpbook.composeapp.generated.resources.opening_slide_045
import kmpbook.composeapp.generated.resources.opening_slide_046
import kmpbook.composeapp.generated.resources.opening_slide_047
import kmpbook.composeapp.generated.resources.opening_slide_048
import kmpbook.composeapp.generated.resources.opening_slide_049
import kmpbook.composeapp.generated.resources.opening_slide_050
import kmpbook.composeapp.generated.resources.opening_slide_051
import kmpbook.composeapp.generated.resources.opening_slide_052
import kmpbook.composeapp.generated.resources.opening_slide_053
import kmpbook.composeapp.generated.resources.opening_slide_054
import kmpbook.composeapp.generated.resources.opening_slide_055
import kmpbook.composeapp.generated.resources.opening_slide_056
import kmpbook.composeapp.generated.resources.opening_slide_057
import kmpbook.composeapp.generated.resources.opening_slide_058
import kmpbook.composeapp.generated.resources.opening_slide_059
import kmpbook.composeapp.generated.resources.opening_slide_060
import kmpbook.composeapp.generated.resources.opening_slide_061
import kmpbook.composeapp.generated.resources.opening_slide_062
import kmpbook.composeapp.generated.resources.opening_slide_063
import kmpbook.composeapp.generated.resources.opening_slide_064
import kmpbook.composeapp.generated.resources.opening_slide_065
import kmpbook.composeapp.generated.resources.opening_slide_066
import kmpbook.composeapp.generated.resources.opening_slide_067
import kmpbook.composeapp.generated.resources.opening_slide_068
import kmpbook.composeapp.generated.resources.opening_slide_069
import kmpbook.composeapp.generated.resources.opening_slide_070
import kmpbook.composeapp.generated.resources.opening_slide_071
import kmpbook.composeapp.generated.resources.opening_slide_072
import kmpbook.composeapp.generated.resources.opening_slide_073
import kmpbook.composeapp.generated.resources.opening_slide_074
import kmpbook.composeapp.generated.resources.opening_slide_075
import kmpbook.composeapp.generated.resources.opening_slide_076
import kmpbook.composeapp.generated.resources.opening_slide_077
import kmpbook.composeapp.generated.resources.opening_slide_078
import kmpbook.composeapp.generated.resources.opening_slide_079
import kmpbook.composeapp.generated.resources.opening_slide_080
import kmpbook.composeapp.generated.resources.opening_slide_081
import kmpbook.composeapp.generated.resources.opening_slide_082
import kmpbook.composeapp.generated.resources.opening_slide_083
import kmpbook.composeapp.generated.resources.opening_slide_084
import kmpbook.composeapp.generated.resources.opening_slide_085
import kmpbook.composeapp.generated.resources.opening_slide_086
import kmpbook.composeapp.generated.resources.opening_slide_087
import kmpbook.composeapp.generated.resources.opening_slide_088
import kmpbook.composeapp.generated.resources.opening_slide_089
import kmpbook.composeapp.generated.resources.opening_slide_090
import kmpbook.composeapp.generated.resources.opening_slide_091
import kmpbook.composeapp.generated.resources.opening_slide_092
import kmpbook.composeapp.generated.resources.opening_slide_093
import kmpbook.composeapp.generated.resources.opening_slide_094
import kmpbook.composeapp.generated.resources.opening_slide_095
import kmpbook.composeapp.generated.resources.opening_slide_096
import kmpbook.composeapp.generated.resources.opening_slide_097
import kmpbook.composeapp.generated.resources.opening_slide_098
import kmpbook.composeapp.generated.resources.opening_slide_099
import kmpbook.composeapp.generated.resources.opening_slide_100

internal val openingSlides: List<OpeningSlide> = listOf(
    OpeningSlide(
        image = Res.drawable.opening_slide_nyse_1963,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1963",
        credit = "Thomas J. O’Halloran · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_nasdaq_2021,
        market = "NASDAQ MARKETSITE · TIMES SQUARE",
        year = "2021",
        credit = "Ajay Suresh · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_yeouido_1984,
        market = "여의도 증권거래소 · 트레이딩 플로어",
        year = "1984",
        credit = "서울역사박물관 · Korea Open Government License Type 1",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_004,
        market = "TOKYO STOCK EXCHANGE",
        year = "2009",
        credit = "ehnmark · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_005,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2022",
        credit = "Kidfly182 · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_006,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2022",
        credit = "Kidfly182 · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_007,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "Kidfly182 · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_008,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "Kidfly182 · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_009,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "Kidfly182 · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_010,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "Kidfly182 · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_011,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "Kidfly182 · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_012,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Kidfly182 · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_013,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Ferfive · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_014,
        market = "TORONTO · FINANCIAL DISTRICT",
        year = "2004",
        credit = "paul (dex) · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_015,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2010",
        credit = "mark.watmough · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_016,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2012",
        credit = "Ken Lund · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_017,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2013",
        credit = "Michielderoo · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_018,
        market = "SAN FRANCISCO · FINANCIAL DISTRICT",
        year = "2014",
        credit = "Noah_Loverbear · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_019,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2012",
        credit = "Ken Lund · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_020,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2021",
        credit = "Percival Kestreltail · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_021,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2022",
        credit = "Don Ramey Logan · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_022,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2023",
        credit = "Josh B · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_023,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2023",
        credit = "Horizon206 · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_024,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2022",
        credit = "ThibautRe · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_025,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2024",
        credit = "MemeGod27 · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_026,
        market = "SAN FRANCISCO · FINANCIAL DISTRICT",
        year = "2022",
        credit = "Wehrstossgewehr · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_027,
        market = "SINGAPORE · FINANCIAL DISTRICT",
        year = "2023",
        credit = "Daniel Case · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_028,
        market = "SINGAPORE · FINANCIAL DISTRICT",
        year = "2023",
        credit = "Daniel Case · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_029,
        market = "HYDERABAD · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Tushar0034 · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_030,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Julian Lupyan · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_031,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Julian Lupyan · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_032,
        market = "WALL STREET · FINANCIAL DISTRICT",
        year = "2025",
        credit = "Julian Lupyan · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_033,
        market = "CURRENCY EXCHANGE · HONG KONG",
        year = "2016",
        credit = "Kuk Yau Wang · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_034,
        market = "CURRENCY MARKET · TEL AVIV",
        year = "1984",
        credit = "Efi Sharir · CC BY 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_035,
        market = "CURRENCY EXCHANGE · HONG KONG",
        year = "2025",
        credit = "BGOOLAEDI Hailwimu · CC0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_036,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_037,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_038,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_039,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_040,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_041,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2011",
        credit = "Dontworry / Lady Whistler · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_042,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_043,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_044,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_045,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_046,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_047,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_048,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_049,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_050,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_051,
        market = "NEW YORK STOCK EXCHANGE",
        year = "c. 1928",
        credit = "Unknown author · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_052,
        market = "TORONTO STOCK EXCHANGE",
        year = "c. 1937",
        credit = "Alexandra Studios · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_053,
        market = "LONDON STOCK EXCHANGE",
        year = "1955",
        credit = "Ben Brooksbank · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_054,
        market = "NEW YORK STOCK EXCHANGE",
        year = "2013",
        credit = "Dario Cantatore · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_055,
        market = "NEW YORK STOCK EXCHANGE",
        year = "2012",
        credit = "CIA · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_056,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1980–2006",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_057,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1980–1990",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_058,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1980–1990",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_059,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1980–2006",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_060,
        market = "NEW YORK STOCK EXCHANGE",
        year = "1980–2006",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_061,
        market = "NEW YORK STOCK EXCHANGE",
        year = "2012",
        credit = "Ken Lund · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_062,
        market = "NEW YORK STOCK EXCHANGE",
        year = "2012",
        credit = "Ken Lund · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_063,
        market = "IRAQ STOCK EXCHANGE · BAGHDAD",
        year = "2004",
        credit = "U.S. Department of Defense · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_064,
        market = "IRAQ STOCK EXCHANGE · BAGHDAD",
        year = "2004",
        credit = "U.S. Department of Defense · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_065,
        market = "NEW YORK STOCK EXCHANGE",
        year = "2011",
        credit = "Carol M. Highsmith · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_066,
        market = "TORONTO STOCK EXCHANGE",
        year = "1956",
        credit = "Chris Lund · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_067,
        market = "TORONTO STOCK EXCHANGE",
        year = "1930",
        credit = "The Financial Post · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_068,
        market = "TOKYO STOCK EXCHANGE",
        year = "2004",
        credit = "Chris 73 · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_069,
        market = "TOKYO STOCK EXCHANGE",
        year = "2008",
        credit = "Stéfan · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_070,
        market = "TOKYO STOCK EXCHANGE",
        year = "2011",
        credit = "Dick Johnson · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_071,
        market = "TOKYO STOCK EXCHANGE",
        year = "2011",
        credit = "Dick Thomas Johnson · CC BY 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_072,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2005",
        credit = "THOMAS~commonswiki · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_073,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2008",
        credit = "Dontworry · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_074,
        market = "SANTOS COFFEE EXCHANGE",
        year = "2007",
        credit = "Fabio Luiz · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_075,
        market = "SANTOS COFFEE EXCHANGE",
        year = "2008",
        credit = "Fernando Mafra · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_076,
        market = "SANTOS COFFEE EXCHANGE",
        year = "2008",
        credit = "Niels Elgaard Larsen · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_077,
        market = "HONG KONG STOCK EXCHANGE",
        year = "2008",
        credit = "Hk1992 · CC BY 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_078,
        market = "HONG KONG STOCK EXCHANGE",
        year = "2005",
        credit = "heycreation · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_079,
        market = "HONG KONG STOCK EXCHANGE",
        year = "2007",
        credit = "WiNG · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_080,
        market = "FRANKFURT STOCK EXCHANGE",
        year = "2015",
        credit = "Ank Kumar · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_081,
        market = "HONG KONG STOCK EXCHANGE",
        year = "2017",
        credit = "384 · CC BY-SA 4.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_082,
        market = "BOMBAY STOCK EXCHANGE",
        year = "2008",
        credit = "Ajay Tallam · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_083,
        market = "BOMBAY STOCK EXCHANGE",
        year = "2011",
        credit = "AroundTheGlobe · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_084,
        market = "BOMBAY STOCK EXCHANGE",
        year = "2011",
        credit = "AroundTheGlobe · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_085,
        market = "BOMBAY STOCK EXCHANGE",
        year = "2011",
        credit = "AroundTheGlobe · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_086,
        market = "BOMBAY STOCK EXCHANGE",
        year = "2012",
        credit = "Surendra Chaurasiya · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_087,
        market = "KOREA EXCHANGE · SEOUL",
        year = "2012",
        credit = "hyolee2 · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_088,
        market = "LONDON STOCK EXCHANGE",
        year = "2007",
        credit = "Kaihsu Tai · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_089,
        market = "LONDON STOCK EXCHANGE",
        year = "2007",
        credit = "Kaihsu Tai · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_090,
        market = "LONDON STOCK EXCHANGE",
        year = "2007",
        credit = "Kaihsu Tai · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_091,
        market = "LONDON STOCK EXCHANGE",
        year = "2007",
        credit = "Kaihsu Tai · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_092,
        market = "LONDON STOCK EXCHANGE",
        year = "2007",
        credit = "Kaihsu Tai · Public domain",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_093,
        market = "PARIS BOURSE",
        year = "2004",
        credit = "Pol at French Wikipedia · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_094,
        market = "PARIS BOURSE",
        year = "2011",
        credit = "Mbzt · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_095,
        market = "PARIS BOURSE",
        year = "2011",
        credit = "Viault · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_096,
        market = "PARIS BOURSE",
        year = "2009",
        credit = "besopha · CC BY-SA 2.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_097,
        market = "SHANGHAI STOCK EXCHANGE",
        year = "2005",
        credit = "Manuel Pajer · CC BY-SA 2.0 de",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_098,
        market = "SHANGHAI STOCK EXCHANGE",
        year = "2007",
        credit = "WiNG · CC BY-SA 3.0",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_099,
        market = "SHANGHAI STOCK EXCHANGE",
        year = "2008",
        credit = "Baycrest · CC BY-SA 2.5",
    ),
    OpeningSlide(
        image = Res.drawable.opening_slide_100,
        market = "SHANGHAI STOCK EXCHANGE",
        year = "2003",
        credit = "螺钉 · CC BY-SA 3.0",
    ),
)
