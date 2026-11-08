"""Add the missing tourist areas (location_images) and re-rank every area north to south.

Fill TOKEN below with an admin bearer token, then run:

    py goroute/scripts/seed_location_areas.py            # does it
    py goroute/scripts/seed_location_areas.py --dry-run  # prints what it would do
    py goroute/scripts/seed_location_areas.py --restore-wards  # after the ward-links fix is deployed

What it does:
1. Creates each area in NEW_AREAS whose fullAddress does not exist yet (safe to re-run).
   Every area gets slogan, the six description sections, coordinates, radius, provinceCode
   and wardCodes. wardCodes were resolved on prod with /public/geo/wards/at and exclude
   any ward that holds another area's anchor. imageUrl is PLACEHOLDER_IMAGE, because the
   API requires one; replace it in the admin console.
2. Re-ranks every area: the five in TOP_AREAS keep 200..196, every other area gets
   195, 194, ... ordered by anchor latitude, north first.
3. Fills provinceCode on existing areas that have none (PROVINCE_FILL).

PUT only writes the fields it sends, so step 2 and 3 leave every other field alone.
"""

from __future__ import annotations

import json
import sys
import unicodedata
import urllib.error
import urllib.request

TOKEN = ""  # paste the admin bearer token here, without the "Bearer " prefix
BASE_URL = "https://onestudy.id.vn/goroute/v1/api/location-images"

# The backend's own fallback image. Replace per area once you have real photos.
PLACEHOLDER_IMAGE = "https://images.unsplash.com/photo-1488646953014-85cb44e25828"

TOP_AREAS = [
    "fdb3261c-613f-4157-b072-64d6b8136878",  # Hà Nội
    "4b6a1ec6-827f-4973-8735-ab70d8f91381",  # TP. Hồ Chí Minh
    "e9ec1c97-9e4a-4c0f-8133-0ae6ac31a483",  # Huế
    "b9d09336-51a6-4e32-8ea6-26523e13cd54",  # Đà Nẵng
    "91573f86-05fa-4ef2-b000-c47e709d57d3",  # Hội An
]

# Existing areas saved without a provinceCode. Codes are the 34-province list, resolved
# from each anchor on prod; the four anchors that sit in the sea were set by hand.
PROVINCE_FILL = {
    "551c5cbb-d821-459a-bbb2-ae0dce372fe7": "08",  # Hoàng Su Phì
    "e61dac16-615f-4ecb-9903-dc101ce53acc": "19",  # Bắc Kạn (Ba Bể)
    "5c391fac-e150-4974-b6f5-004f6ef266d4": "11",  # Điện Biên
    "97f51a9c-50ec-446e-986c-66b243cfc8a8": "15",  # Y Tý
    "223f3279-c033-4c37-8424-515727f84d98": "12",  # Pu Ta Leng (Lai Châu)
    "00fd2a72-e8e7-4d16-9451-0257a7f37250": "14",  # Mộc Châu
    "45019369-429b-41b1-8ffd-14e737614d8d": "25",  # Mai Châu
    "8698720e-116f-4dbc-ae53-07c04c86cafc": "25",  # Tam Đảo
    "167d27b6-63f9-4cc1-ae22-68383660e3bc": "22",  # Hạ Long
    "3dbb35f4-0af6-427b-ac97-028f9e6f22e7": "31",  # Cát Bà
    "6bc2ccec-8874-4097-9af0-b670b4abb6cf": "38",  # Pù Luông (Thanh Hóa)
    "6c0ac37e-67a2-4613-87a7-f1c8ebc81306": "38",  # Sầm Sơn
    "816c34cd-59d0-4707-b4b9-d1a011dae7a3": "40",  # Nghệ An
    "8d64f552-592a-47fe-9ce1-7f0f791e9e07": "44",  # Quảng Bình
    "6faf72a2-e8a5-4ac0-a2bf-27b175615024": "48",  # Cù Lao Chàm
    "08e138d8-b94a-4db2-850c-6bf53daf5aa3": "51",  # Lý Sơn (Quảng Ngãi)
    "29a17b6c-b72e-49ca-a0a0-63f4fd8d84f4": "52",  # Gia Lai (Pleiku)
    "dbd6d661-f8ad-4ae0-9085-377c7a20fe0c": "52",  # Quy Nhơn
    "a6ef7816-73a2-4207-be0b-1d73a3a853a6": "51",  # Măng Đen
    "ebede0d7-fc9d-4b93-a9cd-792e08093acb": "66",  # Buôn Ma Thuột (Đắk Lắk)
    "933b887f-8e7a-4bee-b2ba-6df990e63ff5": "56",  # Dốc Lết
    "e95e4976-aa93-4486-9239-fb22ce38ce56": "56",  # Vĩnh Hy
    "fb28ff5a-2636-4d1a-a28d-bb5ba0d106ef": "56",  # Phan Rang - Tháp Chàm
    "b0ff619a-dcc9-422f-a7c2-629a225b7282": "56",  # Ninh Chữ (Ninh Thuận)
    "36584493-20e0-42fd-b89e-47c29655a775": "68",  # Mũi Né
    "b8e84de9-f513-468c-bbc2-8acdaf0042c3": "80",  # Tây Ninh
    "d0175d1d-3ce8-4acb-865b-50d2bd1fc3e6": "79",  # Vũng Tàu
    "3293662f-ac46-458e-bcfa-fb38e2736bf2": "82",  # Mỹ Tho (Tiền Giang)
    "bf55672f-185c-4fa2-83f8-3192f030cbca": "82",  # Sa Đéc (Đồng Tháp)
    "bb409ba7-69c6-458a-a42f-588aa5c52cb8": "86",  # Trà Vinh
    "fc38e894-80ae-459a-9bee-507e131559b1": "92",  # Cần Thơ
    "b39d8a5a-30b5-4c75-aaaa-acb7f52086ca": "91",  # An Giang (Châu Đốc)
    "851a0dc1-96d8-4413-9f59-4b62c8f64975": "91",  # Nam Du (Kiên Giang)
    "daef3a89-d65a-4138-80c1-83e909023940": "96",  # Cà Mau
    "ca06f33a-56c3-4324-86d6-4fd1c25d6502": "79",  # Côn Đảo
    "4975e4da-c114-4a57-aa4c-8c3daa95842d": "91",  # Phú Quốc
}


def area(name, slogan, lat, lng, radius, province, wards,
         description, vibe, stay, cuisine, season, notes):
    return {
        "fullAddress": name,
        "slogan": slogan,
        "latitude": lat,
        "longitude": lng,
        "coverageRadiusKm": radius,
        "provinceCode": province,
        "wardCodes": wards,
        "description": [
            {"type": "DESCRIPTION", "content": {"title": "", "content": description}},
            {"type": "VIBE", "content": {"title": "Vibe ở đây thế nào", "content": vibe}},
            {"type": "ACCOMODATION", "content": {"title": "Ngủ lại ở đâu", "content": stay}},
            {"type": "CUISINE", "content": {"title": "Đặc sản tại đây", "content": cuisine}},
            {"type": "SEASON", "content": {"title": "Nên đi mùa nào", "content": season}},
            {"type": "NOTES", "content": {"title": "Lưu ý thêm", "content": notes}},
        ],
    }


NEW_AREAS = [
    area("Bắc Hà", "Cao nguyên trắng hoa mận", 22.54, 104.29, 20, "15", ["02839", "02809"],
         "Cao nguyên Lào Cai nổi tiếng với chợ phiên Bắc Hà Chủ nhật, dinh Hoàng A Tưởng, thung lũng mận Tả Van Chư, chợ Cán Cấu và Si Ma Cai.",
         "Sắc màu dân tộc, nhộn nhịp ngày chợ phiên.",
         "Khách sạn, homestay quanh trung tâm Bắc Hà.",
         "Thắng cố, rượu ngô Bản Phố, mận Tam hoa, cải mèn mén.",
         "Tháng 1-3 hoa mận nở trắng; tháng 5-6 mùa mận chín.",
         "Chợ phiên họp sáng Chủ nhật, nên ở lại từ tối thứ Bảy. Cách Sa Pa khoảng 100 km đường núi."),
    area("Lạng Sơn", "Xứ Lạng với nàng Tô Thị", 21.853, 106.761, 25, "20", ["05977", "05986", "06184"],
         "Thành phố biên giới với động Tam Thanh, Nhị Thanh, núi Vọng Phu, chợ Đông Kinh, cửa khẩu Đồng Đăng và khu du lịch núi Mẫu Sơn.",
         "Phố núi nhộn nhịp, thiên nhiên xen chợ biên giới.",
         "Khách sạn trong thành phố Lạng Sơn; nhà nghỉ, homestay trên Mẫu Sơn.",
         "Vịt quay, lợn quay mắc mật, phở chua, khâu nhục, đào Mẫu Sơn.",
         "Tháng 1-2 mùa hoa đào; mùa đông Mẫu Sơn có thể có băng giá; mùa hè trên núi mát.",
         "Cách Hà Nội khoảng 2,5 tiếng theo cao tốc. Đường lên Mẫu Sơn dốc, cần xe khỏe."),
    area("Bình Liêu", "Sống lưng khủng long nơi biên giới", 21.53, 107.40, 20, "22", ["06838", "06856"],
         "Vùng núi biên giới Quảng Ninh với đường tuần tra qua các cột mốc 1300-1305, thác Khe Vằn, đồi lau trắng và bản làng người Tày, Dao.",
         "Hoang sơ, săn mây, hợp đi phượt.",
         "Homestay của người Tày, Dao quanh xã Bình Liêu.",
         "Miến dong Bình Liêu, gà đồi, bánh cooc mò, xôi màu.",
         "Tháng 10-11 mùa hoa lau; tháng 3-4 hoa sở; mùa hè thác đẹp.",
         "Đường lên cột mốc dốc, nên đi xe máy số hoặc ô tô gầm cao. Mang CCCD vì là khu vực biên giới."),
    area("Móng Cái - Trà Cổ", "Nơi bắt đầu dải đất hình chữ S", 21.525, 107.966, 20, "22", ["06712"],
         "Thành phố cửa khẩu cực đông bắc với chợ biên giới, bãi biển Trà Cổ dài hơn 15 km, mũi Sa Vĩ nơi bắt đầu đường bờ biển Việt Nam và đình Trà Cổ.",
         "Sôi động vùng biên, biển rộng và vắng.",
         "Khách sạn ở trung tâm Móng Cái và ven biển Trà Cổ.",
         "Sá sùng, bánh cuốn chả mực, ngán, hải sản Trà Cổ.",
         "Tháng 5-8 mùa tắm biển; tháng 6 âm lịch có lễ hội đình Trà Cổ.",
         "Mang CCCD khi đi khu vực biên giới. Có thể nối tuyến với Bình Liêu và Hạ Long."),
    area("Bắc Ninh", "Đất quan họ Kinh Bắc", 21.18, 106.07, 20, "24", ["09190", "09367", "09400"],
         "Vùng Kinh Bắc với đền Đô thờ tám vua Lý, chùa Dâu, chùa Bút Tháp, làng tranh Đông Hồ, đồi Lim và các làng quan họ.",
         "Cổ kính, đậm văn hóa, hợp đi trong ngày.",
         "Khách sạn ở trung tâm Bắc Ninh và Từ Sơn.",
         "Bánh phu thê Đình Bảng, bánh tẻ Chờ, nem Bùi, gà Hồ, vải Lục Ngạn.",
         "Tháng Giêng âm lịch là mùa lễ hội: hội Lim, hội Gióng, hội chùa Dâu.",
         "Cách Hà Nội khoảng 30 km. Có thể đi thêm Tây Yên Tử và vườn vải Lục Ngạn (Bắc Giang cũ)."),
    area("Ba Vì", "Núi Tản xanh mát ngoại thành Hà Nội", 21.07, 105.38, 25, "01", ["09700", "09574", "09694"],
         "Vùng núi phía tây Hà Nội với vườn quốc gia Ba Vì, đền Thượng, nhà thờ đổ, Khoang Xanh, hồ Suối Hai và làng cổ Đường Lâm ở Sơn Tây.",
         "Trong lành, xanh mát, hợp đi cuối tuần.",
         "Resort, homestay, villa quanh chân núi Ba Vì và hồ Suối Hai.",
         "Sữa và bánh sữa Ba Vì, gà đồi, thịt bê, chè lam Đường Lâm.",
         "Tháng 10-11 mùa hoa dã quỳ; mùa hè mát hơn nội thành.",
         "Cách trung tâm Hà Nội khoảng 60 km. Đường lên đỉnh dốc và nhiều khúc cua, nên đi buổi sáng."),
    area("Quan Lạn - Vân Đồn", "Quần đảo của những bãi cát trắng", 21.065, 107.425, 30, "22", ["06994"],
         "Đặc khu đảo Vân Đồn với hàng trăm đảo lớn nhỏ: Quan Lạn có bãi Sơn Hào, Minh Châu và đình Quan Lạn; cùng Ngọc Vừng, Ba Mùn và cảng Cái Rồng là cửa ngõ ra đảo.",
         "Biển đảo yên tĩnh, ít đông đúc hơn Hạ Long.",
         "Homestay, khách sạn ở Cái Rồng và trên đảo Quan Lạn, Minh Châu.",
         "Sá sùng, chả mực, sam biển, ngán, rượu ba kích.",
         "Tháng 5-8 biển đẹp; tháng 6 âm lịch có lễ hội đình Quan Lạn.",
         "Có sân bay Vân Đồn. Tàu ra đảo phụ thuộc thời tiết, nên xem lịch tàu và thông báo cấm biển trước."),
    area("Cô Tô", "Hòn đảo xanh của vịnh Bắc Bộ", 20.98, 107.765, 15, "22", ["07192"],
         "Đặc khu đảo của Quảng Ninh với biển trong, bãi Hồng Vàn, bãi Vàn Chảy, hải đăng Cô Tô, Cô Tô Con và đảo Thanh Lân hoang sơ.",
         "Hoang sơ, trong lành, hợp đi trốn nắng hè.",
         "Homestay, nhà nghỉ và vài resort nhỏ ở thị trấn Cô Tô.",
         "Mực một nắng, ốc, cù kỳ, hải sản tươi.",
         "Tháng 4-9 biển đẹp nhất; tránh ngày có bão vì tàu ngừng chạy.",
         "Đi tàu cao tốc từ cảng Cái Rồng (Vân Đồn) khoảng 1,5 tiếng. Dịp hè và lễ nên đặt vé tàu, phòng trước."),
    area("Hòa Bình", "Phố núi bên lòng hồ sông Đà", 20.81, 105.34, 30, "25", ["04795", "04792", "05092", "04978"],
         "Thành phố Hòa Bình cũ (nay thuộc Phú Thọ) với hồ thủy điện Hòa Bình, Thung Nai, đền Thác Bờ, bảo tàng Không gian văn hóa Mường và suối khoáng Kim Bôi.",
         "Gần Hà Nội, sông nước và núi rừng.",
         "Khách sạn ở trung tâm; resort, homestay ven hồ và ở suối khoáng Kim Bôi.",
         "Cá sông Đà, thịt lợn mán, cơm lam, rượu cần, cam Cao Phong.",
         "Mùa hè hợp đi thuyền lòng hồ; tháng 11-1 mùa cam Cao Phong.",
         "Cách Hà Nội khoảng 75 km. Có thể đi tiếp Mai Châu hoặc Pù Luông."),
    area("Hưng Yên (Phố Hiến)", "Thứ nhất Kinh Kỳ, thứ nhì Phố Hiến", 20.65, 106.05, 30, "33", ["11953", "12223"],
         "Thương cảng cổ Phố Hiến với Văn Miếu Xích Đằng, chùa Chuông, đền Mẫu, đền Trần, cùng đền Chử Đồng Tử - Tiên Dung ở Khoái Châu.",
         "Yên bình, cổ kính, hợp du lịch tâm linh.",
         "Khách sạn ở trung tâm phường Phố Hiến; đa số khách đi về trong ngày từ Hà Nội.",
         "Nhãn lồng, chè sen long nhãn, tương Bần, gà Đông Tảo, bánh cáy Thái Bình.",
         "Tháng 7-8 mùa nhãn lồng; tháng Giêng, tháng Hai âm lịch mùa lễ hội.",
         "Cách Hà Nội khoảng 60 km. Đền Chử Đồng Tử gần Hà Nội hơn, ghé được trên đường đi."),
    area("Con Cuông - Pù Mát", "Đại ngàn Pù Mát xứ Nghệ", 19.05, 104.88, 25, "40", ["17254"],
         "Vùng núi miền tây Nghệ An với vườn quốc gia Pù Mát, thác Khe Kèm, sông Giăng trong vắt và bản Nưa, bản Xiềng của người Thái.",
         "Sinh thái, mát lành, hợp trekking và tắm suối.",
         "Homestay nhà sàn ở bản Nưa, bản Xiềng; resort sinh thái ven sông Giăng.",
         "Cá mát sông Giăng, gà đồi, cơm lam, rau rừng.",
         "Tháng 4-8 thác và sông đẹp, hợp tắm suối.",
         "Cách Vinh khoảng 130 km. Mùa mưa tháng 9-10 cẩn thận lũ suối."),
    area("Cửa Lò", "Bãi biển xứ Nghệ", 18.81, 105.72, 15, "40", ["16732"],
         "Thị xã biển cạnh thành phố Vinh, có bãi tắm dài, đảo Lan Châu, đảo Ngư, đảo Mắt, cảng cá và gần khu di tích Kim Liên.",
         "Biển gia đình, sôi động mùa hè.",
         "Khách sạn, resort dọc đường Bình Minh ven biển.",
         "Ghẹ, mực nháy, cháo lươn, súp lươn, nhút Thanh Chương.",
         "Tháng 5-8 mùa tắm biển.",
         "Cuối tuần mùa hè rất đông. Hỏi giá trước khi gọi hải sản."),
    area("Phong Nha", "Vương quốc hang động", 17.59, 106.283, 25, "44", ["19138"],
         "Cửa ngõ vườn quốc gia Phong Nha - Kẻ Bàng, di sản thiên nhiên thế giới, với động Phong Nha, động Thiên Đường, Hang Tối, suối Moọc và các tour thám hiểm hang Sơn Đoòng, hang Én.",
         "Thiên nhiên hùng vĩ, phiêu lưu, mạo hiểm.",
         "Homestay, nhà nghỉ ở thị trấn Phong Nha bên sông Son; resort nhỏ ven rừng.",
         "Gà Tây Trang, cá suối, khoai deo, cháo canh, bánh bột lọc.",
         "Tháng 2-8 khô ráo, các tour hang mở cửa; tháng 9-11 mùa mưa lũ, nhiều hang đóng.",
         "Tour Sơn Đoòng, hang Én phải đặt trước nhiều tháng. Từ Đồng Hới vào khoảng 45 km."),
    area("Quảng Trị (Đông Hà)", "Miền đất lửa bên sông Bến Hải", 16.816, 107.10, 35, "44", ["19333", "19360", "19414", "19501"],
         "Vùng di tích chiến tranh gồm thành cổ Quảng Trị, địa đạo Vịnh Mốc, cầu Hiền Lương - sông Bến Hải, biển Cửa Tùng và nghĩa trang Trường Sơn, với Đông Hà là trung tâm.",
         "Trầm lắng, giàu lịch sử.",
         "Khách sạn ở Đông Hà; nhà nghỉ, resort nhỏ ven biển Cửa Tùng, Cửa Việt.",
         "Cháo bột cá lóc, bánh ướt Phương Lang, bánh khoái, hải sản Cửa Tùng.",
         "Tháng 3-8 khô ráo; tháng 9-11 nhiều mưa bão.",
         "Mùa hè có gió Lào rất nóng, nên đi sáng sớm. Huế cách khoảng 70 km, đi trong ngày được."),
    area("Kon Tum", "Phố núi bên dòng Đắk Bla", 14.35, 108.0, 25, "51", ["23293"],
         "Thành phố bắc Tây Nguyên với nhà thờ gỗ hơn trăm năm, Tòa Giám mục, cầu treo Kon Klor, nhà rông và các làng Ba Na ven sông Đắk Bla.",
         "Yên ả, mộc mạc, đậm văn hóa Tây Nguyên.",
         "Khách sạn, homestay trong thành phố; lên Măng Đen khoảng 1,5 tiếng.",
         "Gỏi lá, cơm lam, gà nướng, cá suối, rượu cần.",
         "Mùa khô tháng 11-4 đẹp nhất; tháng 3-4 hoa cà phê nở.",
         "Hay đi kết hợp với Măng Đen và Pleiku. Hỏi ý người dân trước khi chụp ảnh trong làng."),
    area("Buôn Đôn", "Xứ săn voi Tây Nguyên", 12.88, 107.78, 20, "66", ["24235"],
         "Vùng đất của người Ê Đê, M'nông, Lào bên sông Sêrêpôk, có cầu treo Buôn Đôn, nhà sàn cổ, mộ vua săn voi và vườn quốc gia Yok Đôn.",
         "Hoang dã, mộc mạc, đậm văn hóa bản địa.",
         "Homestay, nhà sàn, resort sinh thái ở khu du lịch Buôn Đôn; khách sạn ở Buôn Ma Thuột.",
         "Cơm lam, gà nướng, cá lăng nướng, rượu cần.",
         "Mùa khô tháng 11-4; tháng 3 có lễ hội cà phê Buôn Ma Thuột.",
         "Cách Buôn Ma Thuột khoảng 40 km. Không nên cưỡi voi; chọn tour đi bộ cùng voi."),
    area("Đắk Nông (Tà Đùng)", "Vịnh Hạ Long giữa đại ngàn Tây Nguyên", 12.0, 107.69, 40, "68", ["24617", "24637", "24615", "24611"],
         "Vùng Gia Nghĩa - Tà Đùng (Đắk Nông cũ) thuộc công viên địa chất toàn cầu UNESCO, có hồ Tà Đùng với hàng chục đảo, thác Liêng Nung và thác Đắk G'lun.",
         "Hoang sơ, mát mẻ, săn mây.",
         "Khách sạn ở Gia Nghĩa; homestay, lều trại ven hồ Tà Đùng.",
         "Cà phê, hồ tiêu, cá lăng hồ Tà Đùng, gà nướng, cơm lam.",
         "Tháng 11-4 khô ráo, dễ săn mây ở Tà Đùng.",
         "Gia Nghĩa và hồ Tà Đùng cách nhau khoảng 50 km đường đèo. Đường vào Tà Đùng dốc, nên đi ban ngày."),
    area("Cam Ranh - Bình Ba", "Đảo tôm hùm và bãi Dài nắng gió", 11.92, 109.16, 20, "56", ["22420", "22480", "22453"],
         "Vùng biển phía nam Khánh Hòa với đảo Bình Ba nổi tiếng tôm hùm, bãi Dài nhiều resort, vịnh Cam Ranh và sân bay quốc tế ngay cạnh.",
         "Hoang sơ trên đảo, sang trọng ở bãi Dài.",
         "Resort ở bãi Dài; homestay, nhà nghỉ trên đảo Bình Ba.",
         "Tôm hùm Bình Ba, ốc, nhum, mực, xoài Cam Lâm.",
         "Tháng 1-8 biển êm, nắng đẹp; tháng 10-12 mưa và sóng lớn.",
         "Ra đảo Bình Ba bằng tàu từ cảng Ba Ngòi. Kết hợp được với Nha Trang ở phía bắc và Vĩnh Hy ở phía nam."),
    area("Bảo Lộc", "Thủ phủ trà và cà phê của Lâm Đồng", 11.548, 107.807, 25, "68", ["24820", "24841", "24823"],
         "Thành phố cao nguyên phía nam Lâm Đồng giữa đồi chè, vườn cà phê và nhiều thác nước như thác Dambri, cùng đồi chè Tâm Châu và Thiền viện Bát Nhã.",
         "Chill, yên bình, mát mẻ, hợp đi cuối tuần.",
         "Homestay, farmstay giữa đồi chè và khách sạn ở trung tâm thành phố.",
         "Trà ô long, cà phê, bơ, sầu riêng, bánh canh chả cá, hồng treo gió.",
         "Tháng 11-4 khô ráo, đẹp nhất; mùa mưa tháng 5-10 thác đổ mạnh nhưng đường trơn.",
         "Cách TP.HCM khoảng 180 km, là trạm dừng quen thuộc trên đường lên Đà Lạt. Các điểm nằm rải rác, nên đi xe máy hoặc ô tô."),
    area("Nam Cát Tiên (Đồng Nai)", "Rừng nhiệt đới bên sông Đồng Nai", 11.42, 107.43, 20, "75", ["26122"],
         "Vườn quốc gia Cát Tiên, khu dự trữ sinh quyển thế giới, với Bàu Sấu, rừng nguyên sinh, cây tung cổ thụ, trung tâm cứu hộ gấu và làng Tà Lài của người S'tiêng.",
         "Hoang dã, trong lành, hợp trekking và xem chim thú.",
         "Nhà nghỉ trong vườn quốc gia; homestay, farmstay ở làng Tà Lài.",
         "Cơm lam, gà nướng, cá sông Đồng Nai, rượu cần.",
         "Tháng 12-4 mùa khô, dễ đi rừng và Bàu Sấu.",
         "Cách TP.HCM khoảng 150 km. Mùa mưa rừng nhiều vắt, nên mang tất chống vắt; đăng ký trước nếu muốn đi đêm soi thú."),
    area("Phú Quý", "Đảo ngọc giữa biển Đông", 10.52, 108.94, 10, "68", ["23272"],
         "Đặc khu đảo ngoài khơi Lâm Đồng (Bình Thuận cũ) với nước biển trong, Gành Hang, cột cờ, chùa Linh Sơn, bãi Triều Dương cùng Hòn Tranh, Hòn Đen lân cận.",
         "Hoang sơ, ít khách, yên bình.",
         "Homestay, nhà nghỉ và vài khách sạn nhỏ trên đảo.",
         "Hải sản, cua mặt trăng, ốc, bò Phú Quý.",
         "Tháng 3-8 biển êm; từ tháng 9 có gió mùa, tàu hay nghỉ chạy.",
         "Đi tàu cao tốc từ cảng Phan Thiết khoảng 2,5-3 tiếng, dễ say sóng. Thuê xe máy để đi vòng đảo."),
    area("Hồ Tràm - Hồ Cốc", "Biển rừng cuối tuần của Sài Gòn", 10.49, 107.46, 20, "79", ["26620", "26656"],
         "Dải biển Xuyên Mộc nơi rừng Bình Châu - Phước Bửu chạy sát biển, có bãi Hồ Tràm, Hồ Cốc, suối khoáng nóng Bình Châu và nhiều resort.",
         "Nghỉ dưỡng, yên tĩnh, hợp gia đình và cặp đôi.",
         "Resort, villa ven biển Hồ Tràm, Hồ Cốc; homestay ở Bình Châu.",
         "Hải sản Bình Châu, cháo hàu, bánh khọt, trứng luộc suối khoáng.",
         "Tháng 11-4 nắng ráo, biển êm.",
         "Cách TP.HCM khoảng 2-2,5 tiếng. Cuối tuần và lễ rất đông, nên đặt phòng sớm."),
    area("Cần Giờ", "Lá phổi xanh của Sài Gòn", 10.47, 106.92, 12, "79", ["27664", "27676"],
         "Vùng biển duy nhất của TP.HCM với rừng ngập mặn Cần Giờ, khu du lịch rừng Sác, đảo Khỉ, biển 30/4, chợ Hàng Dương và xã đảo Thạnh An.",
         "Sinh thái, gần gũi thiên nhiên, hợp đi trong ngày.",
         "Resort, homestay ven biển Cần Thạnh; đa số khách đi về trong ngày.",
         "Hải sản, ốc len, tôm tít, hàu, cá khô.",
         "Mùa khô tháng 12-4 dễ đi; mùa mưa rừng xanh nhưng nhiều muỗi.",
         "Cách trung tâm TP.HCM khoảng 50 km, phải qua phà Bình Khánh. Mang thuốc chống muỗi khi vào rừng; giữ đồ khi đi đảo Khỉ."),
    area("Hòn Sơn", "Đảo núi giữa vịnh Thái Lan", 9.80, 104.63, 10, "91", [],
         # No wardCodes: Đặc khu Kiên Hải also holds Nam Du, so the ward would pull Nam Du's places here.
         "Đảo thuộc đặc khu Kiên Hải với đỉnh Ma Thiên Lãnh, bãi Bàng, bãi Xếp, bãi Thiên Tuế và Hòn Mấu, ít khách hơn Nam Du.",
         "Hoang sơ, bình yên, nhịp sống làng chài.",
         "Homestay, nhà nghỉ quanh bãi Bàng và bãi Nhà.",
         "Nhum biển, ốc, cá trích, gỏi cá, hải sản nướng.",
         "Tháng 11-4 biển êm, nắng đẹp.",
         "Đi tàu cao tốc từ Rạch Giá khoảng 1,5 tiếng. Leo Ma Thiên Lãnh nên đi sáng sớm và mang đủ nước."),
    area("Sóc Trăng", "Xứ chùa Khmer miền Tây", 9.603, 105.973, 30, "92", ["31510", "31591", "31633"],
         "Vùng đất giao thoa văn hóa Kinh, Hoa, Khmer với hàng trăm chùa Khmer như chùa Dơi, chùa Chén Kiểu, chùa Kh'Leang và cù lao Dung.",
         "Bình yên, đậm văn hóa Khmer, nhiều sắc màu tâm linh.",
         "Khách sạn ở trung tâm Sóc Trăng; homestay miệt vườn ở cù lao Dung.",
         "Bún nước lèo, bánh pía, bánh cống, lạp xưởng, mè láo.",
         "Mùa khô tháng 12-4 dễ đi; tháng 10 âm lịch có lễ Oóc Om Bóc và đua ghe ngo.",
         "Ăn mặc kín đáo khi vào chùa. Từ Cần Thơ khoảng 1,5 tiếng, đi tiếp Bạc Liêu được trong một hành trình."),
    area("Bạc Liêu", "Đất Công tử và cánh đồng điện gió", 9.285, 105.725, 25, "96", ["31825", "31840", "31834"],
         "Thành phố ven biển miền Tây gắn với giai thoại Công tử Bạc Liêu, nghệ thuật đờn ca tài tử, cánh đồng điện gió và Quán Âm Phật Đài Nhà Mát.",
         "Thong thả, hào sảng, đậm chất Nam Bộ.",
         "Khách sạn, nhà nghỉ ở trung tâm thành phố Bạc Liêu.",
         "Bánh xèo, bún bò cay, ba khía, muối ớt, hải sản.",
         "Mùa khô tháng 12-4 trời đẹp, ít mưa.",
         "Cánh đồng điện gió đẹp nhất lúc hoàng hôn. Có thể đi chung với Sóc Trăng hoặc xuống tiếp Cà Mau."),
]

DRY_RUN = "--dry-run" in sys.argv


def call(method: str, url: str, body: dict | None = None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {TOKEN}",
        "Content-Type": "application/json",
        "Accept": "application/json",
        # The proxy answers 403 to urllib's default User-Agent.
        "User-Agent": "Mozilla/5.0 goroute-seed-location-areas",
    })
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))["data"]
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {url} -> {error.code}: {error.read().decode('utf-8', 'replace')[:500]}")


def key(name: str) -> str:
    stripped = unicodedata.normalize("NFD", name.lower())
    return " ".join("".join(c for c in stripped if unicodedata.category(c) != "Mn").replace("đ", "d").split())


def restore_wards() -> None:
    """Puts NEW_AREAS' wardCodes back on areas that have none.

    The first import was rolled back to empty wards because the area list endpoint threw on
    any ward link (unquoted aliases in LocationImageWardMapper.findAllLinks). Run this only on
    a backend that has that fix, or the list endpoint fails again.
    """
    rows = {key(row["fullAddress"]): row for row in call("GET", BASE_URL)}
    for new_area in NEW_AREAS:
        row = rows.get(key(new_area["fullAddress"]))
        if row is None or row.get("wardCodes") or not new_area["wardCodes"]:
            continue
        print(f"wards  {row['fullAddress']}: {new_area['wardCodes']}")
        if not DRY_RUN:
            call("PUT", f"{BASE_URL}/{row['id']}", {"wardCodes": new_area["wardCodes"]})
    if not DRY_RUN:
        call("GET", BASE_URL)  # fails loudly if the list endpoint still cannot read ward links
        print("area list still loads.")


def main() -> None:
    if not TOKEN:
        raise SystemExit("Fill TOKEN at the top of the file first.")
    if "--restore-wards" in sys.argv:
        restore_wards()
        return

    rows = call("GET", BASE_URL)
    existing = {key(row["fullAddress"]) for row in rows}
    pending = []
    for new_area in NEW_AREAS:
        if key(new_area["fullAddress"]) in existing:
            print(f"skip   {new_area['fullAddress']} (already exists)")
        else:
            pending.append({**new_area, "id": f"new:{new_area['fullAddress']}"})

    top = {area_id: 200 - index for index, area_id in enumerate(TOP_AREAS)}
    rest = sorted((row for row in rows + pending if row["id"] not in top),
                  key=lambda row: -float(row["latitude"]))
    priorities = {**top, **{row["id"]: 200 - len(top) - index for index, row in enumerate(rest)}}

    created = []
    for new_area in pending:
        body = {k: v for k, v in new_area.items() if k != "id"}
        body.update(imageUrl=PLACEHOLDER_IMAGE, priority=priorities[new_area["id"]])
        print(f"create {body['fullAddress']} priority={body['priority']} wards={body['wardCodes']}")
        if not DRY_RUN:
            row = call("POST", BASE_URL, body)
            created.append(row)

    updated = 0
    for row in rows:
        patch = {}
        if row.get("priority") != priorities[row["id"]]:
            patch["priority"] = priorities[row["id"]]
        if not row.get("provinceCode") and row["id"] in PROVINCE_FILL:
            patch["provinceCode"] = PROVINCE_FILL[row["id"]]
        if not patch:
            continue
        print(f"update {row['fullAddress']}: {patch}")
        if not DRY_RUN:
            call("PUT", f"{BASE_URL}/{row['id']}", patch)
        updated += 1

    if DRY_RUN:
        print(f"\n(dry run) nothing was written; {updated} areas would be updated.")
        return
    print(f"\ncreated {len(created)}, updated {updated}.")
    if created:
        print("New areas still on the placeholder image:")
        for row in created:
            print(f"  {row['fullAddress']}: {row['id']}")


if __name__ == "__main__":
    main()
