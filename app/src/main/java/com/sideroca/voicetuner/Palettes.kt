package com.sideroca.voicetuner

/**
 * 配色库（64 套：现代经典 18 + 中国传统色 46；色值来自闪译之 Color Atlas 场景调色板）
 * —— 对齐《夕汀前端规范》：**11 个语义槽**（bg/card/accent/text/subText/panelBg/panelText/panelSub/barBg/barText/cardStroke）
 *    + **4 个形状/质感参数**（cardRadius / btnRadius / elev / solidBtn）。
 * 生成来源：/workspace/夕汀前端规范/palettes.json（以它为数值事实；名字与分组沿用 App 原有写法）
 */
data class Pal(
    val id: String, val name: String, val group: String, val dark: Boolean,
    val bg: Int, val card: Int, val accent: Int, val text: Int, val subText: Int,
    val panelBg: Int, val panelText: Int, val panelSub: Int, val barBg: Int, val barText: Int, val stroke: Int,
    val cardRadius: Int, val btnRadius: Int, val elev: Int, val solidBtn: Boolean
)

object Palettes {
    val all: List<Pal> = listOf(
        Pal("deepseek", "深海蓝", "modern", false, 0xFFF7F8FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF4D6BFE.toInt(), 0xFF1F2329.toInt(), 0xFF828A9B.toInt(), 0xFFFFFFFF.toInt(), 0xFF1F2329.toInt(), 0xFF828A9B.toInt(), 0xFFF0C239.toInt(), 0xFF1F2329.toInt(), 0xFFE3E7EF.toInt(), 12, 12, 4, true),
        Pal("ios", "iOS 经典", "modern", false, 0xFFF2F2F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF007AFF.toInt(), 0xFF000000.toInt(), 0xFF8E8E93.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF8E8E93.toInt(), 0xFFED5736.toInt(), 0xFFFFFFFF.toInt(), 0xFFC6C6C8.toInt(), 10, 10, 0, true),
        Pal("kimi", "月白紫", "modern", false, 0xFFFFFFFF.toInt(), 0xFFF7F9FC.toInt(), 0xFF007CFF.toInt(), 0xFF002F5B.toInt(), 0xFF8A99AC.toInt(), 0xFFFFFFFF.toInt(), 0xFF002F5B.toInt(), 0xFF8A99AC.toInt(), 0xFF9D2933.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3E9F4.toInt(), 12, 12, 4, true),
        Pal("qq", "QQ 经典蓝", "modern", false, 0xFFF0F4F8.toInt(), 0xFFFFFFFF.toInt(), 0xFF12B7F5.toInt(), 0xFF1A1A1A.toInt(), 0xFF86909C.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt(), 0xFF86909C.toInt(), 0xFFF0C239.toInt(), 0xFF1A1A1A.toInt(), 0xFFE1E8F0.toInt(), 12, 12, 4, true),
        Pal("notion", "Notion 极简", "modern", false, 0xFFFFFFFF.toInt(), 0xFFF7F6F3.toInt(), 0xFF2EAADC.toInt(), 0xFF37352F.toInt(), 0xFF9B9A97.toInt(), 0xFFFFFFFF.toInt(), 0xFF37352F.toInt(), 0xFF9B9A97.toInt(), 0xFF789262.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9E7E1.toInt(), 6, 6, 0, true),
        Pal("chatgpt", "ChatGPT 极简", "modern", false, 0xFFFFFFFF.toInt(), 0xFFF7F7F8.toInt(), 0xFF10A37F.toInt(), 0xFF0D0D0D.toInt(), 0xFF8E8EA0.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D0D0D.toInt(), 0xFF8E8EA0.toInt(), 0xFFD6ECF0.toInt(), 0xFF0D0D0D.toInt(), 0xFFECECF1.toInt(), 16, 24, 0, true),
        Pal("claude", "Claude 米橙", "modern", false, 0xFFFAF9F5.toInt(), 0xFFF0EEE6.toInt(), 0xFFD97757.toInt(), 0xFF3D3929.toInt(), 0xFF919086.toInt(), 0xFFFAF9F5.toInt(), 0xFF3D3929.toInt(), 0xFF919086.toInt(), 0xFF1A2847.toInt(), 0xFFFFFFFF.toInt(), 0xFFE5E1D6.toInt(), 12, 12, 0, false),
        Pal("perplexity", "Perplexity 青", "modern", false, 0xFFF3F3EE.toInt(), 0xFFFCFCF9.toInt(), 0xFF20808D.toInt(), 0xFF13343B.toInt(), 0xFF7A8B8F.toInt(), 0xFFFCFCF9.toInt(), 0xFF13343B.toInt(), 0xFF7A8B8F.toInt(), 0xFFD6ECF0.toInt(), 0xFF13343B.toInt(), 0xFFE4E4DC.toInt(), 12, 12, 4, true),
        Pal("cat_latte", "Catppuccin 拿铁", "modern", false, 0xFFEFF1F5.toInt(), 0xFFE6E9EF.toInt(), 0xFF1E66F5.toInt(), 0xFF4C4F69.toInt(), 0xFF7C7F93.toInt(), 0xFFFFFFFF.toInt(), 0xFF4C4F69.toInt(), 0xFF8C8FA3.toInt(), 0xFF789262.toInt(), 0xFFFFFFFF.toInt(), 0xFFBCC0CC.toInt(), 12, 12, 0, true),
        Pal("github", "GitHub 夜", "modern", true, 0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF2F81F7.toInt(), 0xFFE6EDF3.toInt(), 0xFF8B949E.toInt(), 0xFF161B22.toInt(), 0xFFE6EDF3.toInt(), 0xFF8B949E.toInt(), 0xFFD6ECF0.toInt(), 0xFF0D1117.toInt(), 0xFF30363D.toInt(), 8, 8, 0, true),
        Pal("discord", "Discord 夜", "modern", true, 0xFF313338.toInt(), 0xFF2B2D31.toInt(), 0xFF5865F2.toInt(), 0xFFDBDEE1.toInt(), 0xFF949BA4.toInt(), 0xFF2B2D31.toInt(), 0xFFDBDEE1.toInt(), 0xFF949BA4.toInt(), 0xFFF0C239.toInt(), 0xFF1A1B26.toInt(), 0xFF3F4147.toInt(), 12, 12, 0, true),
        Pal("cyber", "赛博 2077", "modern", true, 0xFF0D0D10.toInt(), 0xFF16161A.toInt(), 0xFFFCEE0A.toInt(), 0xFFF2F2EE.toInt(), 0xFF9A9A8F.toInt(), 0xFF101014.toInt(), 0xFFF2F2EE.toInt(), 0xFF8F8F86.toInt(), 0xFF1A2847.toInt(), 0xFFF0C239.toInt(), 0xFF33321E.toInt(), 6, 4, 0, true),
        Pal("violet", "紫电夜", "modern", true, 0xFF170F2B.toInt(), 0xFF221742.toInt(), 0xFF9C5CFF.toInt(), 0xFFF1EAFE.toInt(), 0xFF9E90C4.toInt(), 0xFF221742.toInt(), 0xFFF1EAFE.toInt(), 0xFF9E90C4.toInt(), 0xFFF0C239.toInt(), 0xFF221742.toInt(), 0xFF33265A.toInt(), 12, 12, 0, true),
        Pal("cat_mocha", "Catppuccin 摩卡", "modern", true, 0xFF1E1E2E.toInt(), 0xFF313244.toInt(), 0xFF89B4FA.toInt(), 0xFFCDD6F4.toInt(), 0xFFA6ADC8.toInt(), 0xFF181825.toInt(), 0xFFCDD6F4.toInt(), 0xFFA6ADC8.toInt(), 0xFF789262.toInt(), 0xFFFFFFFF.toInt(), 0xFF45475A.toInt(), 12, 12, 0, true),
        Pal("nord", "Nord 北欧", "modern", true, 0xFF2E3440.toInt(), 0xFF3B4252.toInt(), 0xFF88C0D0.toInt(), 0xFFECEFF4.toInt(), 0xFFD8DEE9.toInt(), 0xFF2E3440.toInt(), 0xFFECEFF4.toInt(), 0xFF93A1B5.toInt(), 0xFFD6ECF0.toInt(), 0xFF2E3440.toInt(), 0xFF4C566A.toInt(), 8, 8, 0, true),
        Pal("dracula", "Dracula 霓虹", "modern", true, 0xFF282A36.toInt(), 0xFF44475A.toInt(), 0xFFBD93F9.toInt(), 0xFFF8F8F2.toInt(), 0xFF9AA0C0.toInt(), 0xFF21222C.toInt(), 0xFFF8F8F2.toInt(), 0xFF9AA0C0.toInt(), 0xFFF0C239.toInt(), 0xFF282A36.toInt(), 0xFF6272A4.toInt(), 12, 12, 0, true),
        Pal("tokyo", "Tokyo Night", "modern", true, 0xFF1A1B26.toInt(), 0xFF24283B.toInt(), 0xFF7AA2F7.toInt(), 0xFFC0CAF5.toInt(), 0xFF7982A9.toInt(), 0xFF16161E.toInt(), 0xFFC0CAF5.toInt(), 0xFF7982A9.toInt(), 0xFFF0C239.toInt(), 0xFF1A1B26.toInt(), 0xFF292E42.toInt(), 12, 12, 0, true),
        Pal("ds", "死亡搁浅 · 冷蓝", "modern", true, 0xFF1E262E.toInt(), 0xFF262F38.toInt(), 0xFF7FB4D9.toInt(), 0xFFC8D4DC.toInt(), 0xFF7A8A96.toInt(), 0xFF222B33.toInt(), 0xFFC8D4DC.toInt(), 0xFF7A8A96.toInt(), 0xFF9D2933.toInt(), 0xFFF2F3F7.toInt(), 0xFF3A465E.toInt(), 6, 6, 0, false),
        Pal("song", "宋代美学 · 天水碧", "chinese", false, 0xFFD6ECF0.toInt(), 0xFFFFFFFF.toInt(), 0xFF5AA4AE.toInt(), 0xFF33454F.toInt(), 0xFF758A99.toInt(), 0xFFFFFFFF.toInt(), 0xFF33454F.toInt(), 0xFF758A99.toInt(), 0xFFF0C239.toInt(), 0xFF33454F.toInt(), 0xFF9FBFBF.toInt(), 8, 8, 0, true),
        Pal("tea", "茶文化 · 竹青", "chinese", false, 0xFFDCEDCF.toInt(), 0xFFFFFFFF.toInt(), 0xFF789262.toInt(), 0xFF3B4634.toInt(), 0xFF9E8368.toInt(), 0xFFFFFFFF.toInt(), 0xFF3B4634.toInt(), 0xFF9E8368.toInt(), 0xFFF0C239.toInt(), 0xFF3B4634.toInt(), 0xFFC5D1BC.toInt(), 8, 8, 0, true),
        Pal("ruyao_tianqing", "天青釉 · 汝窑", "chinese", false, 0xFFC7F2F9.toInt(), 0xFFF4FDFE.toInt(), 0xFF438894.toInt(), 0xFF1A2526.toInt(), 0xFF4B676B.toInt(), 0xFFF4FDFE.toInt(), 0xFF1A2526.toInt(), 0xFF4B676B.toInt(), 0xFF7FA9B0.toInt(), 0xFF1A1A1A.toInt(), 0xFFA4D3DB.toInt(), 8, 8, 0, true),
        Pal("longquan_fenqing", "粉青釉 · 龙泉", "chinese", false, 0xFFDBF9E9.toInt(), 0xFFF5FEF9.toInt(), 0xFF439467.toInt(), 0xFF1A261F.toInt(), 0xFF4B6B59.toInt(), 0xFFF5FEF9.toInt(), 0xFF1A261F.toInt(), 0xFF4B6B59.toInt(), 0xFFA8C3B4.toInt(), 0xFF1A1A1A.toInt(), 0xFFC0DBCC.toInt(), 8, 8, 0, true),
        Pal("longquan_meiziqing", "梅子青 · 龙泉", "chinese", false, 0xFFC7F9D8.toInt(), 0xFFF4FEF8.toInt(), 0xFF43945E.toInt(), 0xFF1A261E.toInt(), 0xFF4B6B56.toInt(), 0xFFF4FEF8.toInt(), 0xFF1A261E.toInt(), 0xFF4B6B56.toInt(), 0xFF6F9E7F.toInt(), 0xFF1A1A1A.toInt(), 0xFFA2DBB6.toInt(), 8, 8, 0, true),
        Pal("song_yingqing", "影青 · 宋", "chinese", false, 0xFFF9EDED.toInt(), 0xFFFEFAFA.toInt(), 0xFF023291.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFFEFAFA.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFE0F0E8.toInt(), 0xFF1A1A1A.toInt(), 0xFFD9C5C5.toInt(), 8, 8, 0, true),
        Pal("dingyao_yabai", "牙白 · 定窑", "chinese", false, 0xFFF9EDED.toInt(), 0xFFFEFAFA.toInt(), 0xFF916D37.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFFEFAFA.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFF2EDDE.toInt(), 0xFF1A1A1A.toInt(), 0xFFD9C5C5.toInt(), 8, 8, 0, true),
        Pal("jianzhan_wujin", "乌金釉 · 建盏", "chinese", true, 0xFF291F10.toInt(), 0xFF3A3022.toInt(), 0xFFB1A28E.toInt(), 0xFFF7F3EC.toInt(), 0xFFAB9C87.toInt(), 0xFF3A3022.toInt(), 0xFFF7F3EC.toInt(), 0xFFAB9C87.toInt(), 0xFFA3BCC7.toInt(), 0xFFFFFFFF.toInt(), 0xFF50432F.toInt(), 8, 8, 0, true),
        Pal("jun_tianlan", "钧窑天蓝", "chinese", false, 0xFFC7DEF9.toInt(), 0xFFF4F9FE.toInt(), 0xFF436894.toInt(), 0xFF1A2026.toInt(), 0xFF4B5A6B.toInt(), 0xFFF4F9FE.toInt(), 0xFF1A2026.toInt(), 0xFF4B5A6B.toInt(), 0xFF6E8FB5.toInt(), 0xFFFFFFFF.toInt(), 0xFFA2BDDB.toInt(), 8, 8, 0, true),
        Pal("jun_zihong", "钧窑紫红", "chinese", true, 0xFF2D1A1F.toInt(), 0xFF3E2C30.toInt(), 0xFF96719E.toInt(), 0xFFFAF1F2.toInt(), 0xFFB2969C.toInt(), 0xFF3E2C30.toInt(), 0xFFFAF1F2.toInt(), 0xFFB2969C.toInt(), 0xFF89D1A0.toInt(), 0xFFFFFFFF.toInt(), 0xFF563D43.toInt(), 8, 8, 0, true),
        Pal("langyao_hong", "郎窑红 · 清", "chinese", true, 0xFF2D1B1A.toInt(), 0xFF3F2C2B.toInt(), 0xFFA97C46.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39794.toInt(), 0xFF3F2C2B.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39794.toInt(), 0xFF89D1A0.toInt(), 0xFFFFFFFF.toInt(), 0xFF573E3C.toInt(), 8, 8, 0, true),
        Pal("jiangdou_hong", "豇豆红 · 清", "chinese", false, 0xFFF9C7CC.toInt(), 0xFFFEF4F5.toInt(), 0xFF942430.toInt(), 0xFF261A1B.toInt(), 0xFF6B4B4E.toInt(), 0xFFFEF4F5.toInt(), 0xFF261A1B.toInt(), 0xFF6B4B4E.toInt(), 0xFFC45A65.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBA2A8.toInt(), 8, 8, 0, true),
        Pal("yanzhi_shui", "胭脂水 · 清", "chinese", false, 0xFFF9C7C7.toInt(), 0xFFFEF4F4.toInt(), 0xFF944343.toInt(), 0xFF261A1A.toInt(), 0xFF6B4B4B.toInt(), 0xFFFEF4F4.toInt(), 0xFF261A1A.toInt(), 0xFF6B4B4B.toInt(), 0xFFE7A6A6.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBA4A4.toInt(), 8, 8, 0, true),
        Pal("ming_tianbai", "甜白 · 明", "chinese", false, 0xFFF9EDED.toInt(), 0xFFFEFAFA.toInt(), 0xFF013F91.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFFEFAFA.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFF6F3EC.toInt(), 0xFF1A1A1A.toInt(), 0xFFD9C5C5.toInt(), 8, 8, 0, true),
        Pal("ming_jilan", "霁蓝 · 明", "chinese", true, 0xFF17212F.toInt(), 0xFF283240.toInt(), 0xFF60A7FF.toInt(), 0xFFEFF4FA.toInt(), 0xFF91A0B4.toInt(), 0xFF283240.toInt(), 0xFFEFF4FA.toInt(), 0xFF91A0B4.toInt(), 0xFFE0B027.toInt(), 0xFFFFFFFF.toInt(), 0xFF384658.toInt(), 8, 8, 0, true),
        Pal("qing_chayemo", "茶叶末 · 清", "chinese", true, 0xFF271F0F.toInt(), 0xFF383121.toInt(), 0xFFB4A37E.toInt(), 0xFFF6F3EC.toInt(), 0xFFA89D87.toInt(), 0xFF383121.toInt(), 0xFFF6F3EC.toInt(), 0xFFA89D87.toInt(), 0xFF7FC2DE.toInt(), 0xFFFFFFFF.toInt(), 0xFF4E442F.toInt(), 8, 8, 0, true),
        Pal("ming_kongque", "孔雀绿 · 明", "chinese", true, 0xFF10251F.toInt(), 0xFF233630.toInt(), 0xFF00C09B.toInt(), 0xFFEDF6F3.toInt(), 0xFF8AA69C.toInt(), 0xFF233630.toInt(), 0xFFEDF6F3.toInt(), 0xFF8AA69C.toInt(), 0xFFFF9687.toInt(), 0xFFFFFFFF.toInt(), 0xFF314B43.toInt(), 8, 8, 0, true),
        Pal("ming_fanhong", "矾红 · 明", "chinese", true, 0xFF2D1B19.toInt(), 0xFF3F2C2A.toInt(), 0xFF997B4B.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39794.toInt(), 0xFF3F2C2A.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39794.toInt(), 0xFFDABA70.toInt(), 0xFFFFFFFF.toInt(), 0xFF573E3B.toInt(), 8, 8, 0, true),
        Pal("qinghua_lan", "青花钴蓝", "chinese", true, 0xFF19202F.toInt(), 0xFF2B3140.toInt(), 0xFF78A1FF.toInt(), 0xFFF0F3FA.toInt(), 0xFF949FB4.toInt(), 0xFF2B3140.toInt(), 0xFFF0F3FA.toInt(), 0xFF949FB4.toInt(), 0xFFF3A437.toInt(), 0xFFFFFFFF.toInt(), 0xFF3C4559.toInt(), 8, 8, 0, true),
        Pal("qing_shanyuhuang", "鳝鱼黄 · 清", "chinese", false, 0xFFF9E6C7.toInt(), 0xFFFEFBF4.toInt(), 0xFF94723C.toInt(), 0xFF26221A.toInt(), 0xFF6B5F4B.toInt(), 0xFFFEFBF4.toInt(), 0xFF26221A.toInt(), 0xFF6B5F4B.toInt(), 0xFFB89A6A.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBC5A2.toInt(), 8, 8, 0, true),
        Pal("qin_xuansè", "玄色 · 秦", "chinese", true, 0xFF1F1E2E.toInt(), 0xFF30303F.toInt(), 0xFFA3A3B4.toInt(), 0xFFF2F3FA.toInt(), 0xFF9C9CB3.toInt(), 0xFF30303F.toInt(), 0xFFF2F3FA.toInt(), 0xFF9C9CB3.toInt(), 0xFFC1B5A8.toInt(), 0xFFFFFFFF.toInt(), 0xFF434258.toInt(), 8, 8, 0, true),
        Pal("tang_shiliu", "石榴红 · 唐", "chinese", true, 0xFF2D1B19.toInt(), 0xFF3F2C2A.toInt(), 0xFFA87C4C.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39793.toInt(), 0xFF3F2C2A.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39793.toInt(), 0xFF8AD1A0.toInt(), 0xFFFFFFFF.toInt(), 0xFF573E3B.toInt(), 8, 8, 0, true),
        Pal("tang_feihong", "绯红 · 唐", "chinese", true, 0xFF2D1B18.toInt(), 0xFF3F2C2A.toInt(), 0xFFA47E4E.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39793.toInt(), 0xFF3F2C2A.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39793.toInt(), 0xFFE8B275.toInt(), 0xFFFFFFFF.toInt(), 0xFF573E3A.toInt(), 8, 8, 0, true),
        Pal("tang_zhehuang", "柘黄 · 唐", "chinese", false, 0xFFF9E9C7.toInt(), 0xFFFEFBF4.toInt(), 0xFF946503.toInt(), 0xFF26221A.toInt(), 0xFF6B614B.toInt(), 0xFFFEFBF4.toInt(), 0xFF26221A.toInt(), 0xFF6B614B.toInt(), 0xFFC89B3C.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBC9A2.toInt(), 8, 8, 0, true),
        Pal("gugong_hongqiang", "故宫红墙", "chinese", true, 0xFF2D1B1A.toInt(), 0xFF3F2C2B.toInt(), 0xFF9E7943.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39795.toInt(), 0xFF3F2C2B.toInt(), 0xFFFAF1F0.toInt(), 0xFFB39795.toInt(), 0xFF8AD1A0.toInt(), 0xFFFFFFFF.toInt(), 0xFF573E3C.toInt(), 8, 8, 0, true),
        Pal("yuebai", "月白", "chinese", false, 0xFFF9EDED.toInt(), 0xFFFEFAFA.toInt(), 0xFF416091.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFFEFAFA.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFD6ECF0.toInt(), 0xFF1A1A1A.toInt(), 0xFFD9C5C5.toInt(), 8, 8, 0, true),
        Pal("shilv", "石绿", "chinese", false, 0xFFC7F9F9.toInt(), 0xFFF4FEFE.toInt(), 0xFF219493.toInt(), 0xFF1A2626.toInt(), 0xFF4B6B6B.toInt(), 0xFFF4FEFE.toInt(), 0xFF1A2626.toInt(), 0xFF4B6B6B.toInt(), 0xFF57C3C2.toInt(), 0xFF1A1A1A.toInt(), 0xFFA2DBDB.toInt(), 8, 8, 0, true),
        Pal("shiqing", "石青", "chinese", true, 0xFF10232B.toInt(), 0xFF22353C.toInt(), 0xFF00B5E7.toInt(), 0xFFEDF5F9.toInt(), 0xFF89A3AF.toInt(), 0xFF22353C.toInt(), 0xFFEDF5F9.toInt(), 0xFF89A3AF.toInt(), 0xFFE0B027.toInt(), 0xFFFFFFFF.toInt(), 0xFF304954.toInt(), 8, 8, 0, true),
        Pal("dailan", "黛蓝", "chinese", true, 0xFF18212F.toInt(), 0xFF293240.toInt(), 0xFF8BA6D1.toInt(), 0xFFEFF4FA.toInt(), 0xFF929FB4.toInt(), 0xFF293240.toInt(), 0xFFEFF4FA.toInt(), 0xFF929FB4.toInt(), 0xFFD3B18A.toInt(), 0xFFFFFFFF.toInt(), 0xFF3A4659.toInt(), 8, 8, 0, true),
        Pal("yaqing", "鸦青", "chinese", true, 0xFF0F242B.toInt(), 0xFF22353C.toInt(), 0xFF95A8B0.toInt(), 0xFFEDF5F8.toInt(), 0xFF88A3AE.toInt(), 0xFF22353C.toInt(), 0xFFEDF5F8.toInt(), 0xFF88A3AE.toInt(), 0xFFBEB7A7.toInt(), 0xFFFFFFFF.toInt(), 0xFF2F4953.toInt(), 8, 8, 0, true),
        Pal("xuanqing", "玄青", "chinese", true, 0xFF201E2E.toInt(), 0xFF312F3F.toInt(), 0xFFA39FC7.toInt(), 0xFFF3F2FA.toInt(), 0xFF9D9BB3.toInt(), 0xFF312F3F.toInt(), 0xFFF3F2FA.toInt(), 0xFF9D9BB3.toInt(), 0xFFC7B690.toInt(), 0xFFFFFFFF.toInt(), 0xFF444257.toInt(), 8, 8, 0, true),
        Pal("jiangzi", "绛紫", "chinese", true, 0xFF2D1A1E.toInt(), 0xFF3E2C30.toInt(), 0xFF9675A0.toInt(), 0xFFFAF1F2.toInt(), 0xFFB2969B.toInt(), 0xFF3E2C30.toInt(), 0xFFFAF1F2.toInt(), 0xFFB2969B.toInt(), 0xFF89D1A1.toInt(), 0xFFFFFFFF.toInt(), 0xFF563D42.toInt(), 8, 8, 0, true),
        Pal("ouhe", "藕荷", "chinese", false, 0xFFF9EDED.toInt(), 0xFFFEFAFA.toInt(), 0xFF912340.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFFEFAFA.toInt(), 0xFF241F1F.toInt(), 0xFF6B5C5C.toInt(), 0xFFE4C6D0.toInt(), 0xFF1A1A1A.toInt(), 0xFFD9C5C5.toInt(), 8, 8, 0, true),
        Pal("taohong", "桃红", "chinese", false, 0xFFF9C7D3.toInt(), 0xFFFEF4F7.toInt(), 0xFF944356.toInt(), 0xFF261A1D.toInt(), 0xFF6B4B52.toInt(), 0xFFFEF4F7.toInt(), 0xFF261A1D.toInt(), 0xFF6B4B52.toInt(), 0xFFF4A7B9.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBA2B0.toInt(), 8, 8, 0, true),
        Pal("haitang_hong", "海棠红", "chinese", false, 0xFFF9C7CE.toInt(), 0xFFFEF4F6.toInt(), 0xFF941A2A.toInt(), 0xFF261A1C.toInt(), 0xFF6B4B4F.toInt(), 0xFFFEF4F6.toInt(), 0xFF261A1C.toInt(), 0xFF6B4B4F.toInt(), 0xFFDB5A6B.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBA2AA.toInt(), 8, 8, 0, true),
        Pal("zhusha", "朱砂", "chinese", false, 0xFFF9D6C7.toInt(), 0xFFFEF7F4.toInt(), 0xFF942C00.toInt(), 0xFF261E1A.toInt(), 0xFF6B554B.toInt(), 0xFFFEF7F4.toInt(), 0xFF261E1A.toInt(), 0xFF6B554B.toInt(), 0xFFFF4C00.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBB3A2.toInt(), 8, 8, 0, true),
        Pal("xiangse", "缃色", "chinese", false, 0xFFF9EDC7.toInt(), 0xFFFEFCF4.toInt(), 0xFF946F00.toInt(), 0xFF26231A.toInt(), 0xFF6B634B.toInt(), 0xFFFEFCF4.toInt(), 0xFF26231A.toInt(), 0xFF6B634B.toInt(), 0xFFF0C239.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBCDA2.toInt(), 8, 8, 0, true),
        Pal("chijin", "赤金", "chinese", false, 0xFFF9EAC7.toInt(), 0xFFFEFBF4.toInt(), 0xFF946700.toInt(), 0xFF26231A.toInt(), 0xFF6B614B.toInt(), 0xFFFEFBF4.toInt(), 0xFF26231A.toInt(), 0xFF6B614B.toInt(), 0xFFF2BE45.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBCAA2.toInt(), 8, 8, 0, true),
        Pal("zheshi", "赭石", "chinese", true, 0xFF2D1C15.toInt(), 0xFF3E2D26.toInt(), 0xFFD1947A.toInt(), 0xFFFAF1EE.toInt(), 0xFFB2988E.toInt(), 0xFF3E2D26.toInt(), 0xFFFAF1EE.toInt(), 0xFFB2988E.toInt(), 0xFF5DD38A.toInt(), 0xFFFFFFFF.toInt(), 0xFF563F35.toInt(), 8, 8, 0, true),
        Pal("tanse", "檀", "chinese", false, 0xFFF9CFC7.toInt(), 0xFFFEF6F4.toInt(), 0xFF944335.toInt(), 0xFF261C1A.toInt(), 0xFF6B504B.toInt(), 0xFFFEF6F4.toInt(), 0xFF261C1A.toInt(), 0xFF6B504B.toInt(), 0xFFB36D61.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBABA2.toInt(), 8, 8, 0, true),
        Pal("wanse", "绾", "chinese", false, 0xFFF9D3C7.toInt(), 0xFFFEF7F4.toInt(), 0xFF945543.toInt(), 0xFF261D1A.toInt(), 0xFF6B524B.toInt(), 0xFFFEF7F4.toInt(), 0xFF261D1A.toInt(), 0xFF6B524B.toInt(), 0xFFA98175.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBAFA2.toInt(), 8, 8, 0, true),
        Pal("hupo", "琥珀", "chinese", false, 0xFFF9DCC7.toInt(), 0xFFFEF9F4.toInt(), 0xFF943D00.toInt(), 0xFF261F1A.toInt(), 0xFF6B584B.toInt(), 0xFFFEF9F4.toInt(), 0xFF261F1A.toInt(), 0xFF6B584B.toInt(), 0xFFCA6924.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBBAA2.toInt(), 8, 8, 0, true),
        Pal("yanxia", "烟霞", "chinese", false, 0xFFF9CFD7.toInt(), 0xFFFEF4F6.toInt(), 0xFF944353.toInt(), 0xFF261A1D.toInt(), 0xFF6B4B52.toInt(), 0xFFFEF4F6.toInt(), 0xFF261A1D.toInt(), 0xFF6B4B52.toInt(), 0xFFD8A7B1.toInt(), 0xFF1A1A1A.toInt(), 0xFFDBAFB8.toInt(), 8, 8, 0, true),
        Pal("ailv", "艾绿", "chinese", false, 0xFFE3F9DB.toInt(), 0xFFF7FEF4.toInt(), 0xFF589443.toInt(), 0xFF1D261A.toInt(), 0xFF536B4B.toInt(), 0xFFF7FEF4.toInt(), 0xFF1D261A.toInt(), 0xFF536B4B.toInt(), 0xFFA8BFA0.toInt(), 0xFF1A1A1A.toInt(), 0xFFC4DBBB.toInt(), 8, 8, 0, true),
        Pal("ink", "水墨 · 朱砂", "chinese", true, 0xFF2D1C15.toInt(), 0xFF3E2D26.toInt(), 0xFFD1947B.toInt(), 0xFFFAF1EE.toInt(), 0xFFB2988E.toInt(), 0xFF3E2D26.toInt(), 0xFFFAF1EE.toInt(), 0xFFB2988E.toInt(), 0xFF18C9FF.toInt(), 0xFF1A1210.toInt(), 0xFF563F36.toInt(), 12, 12, 0, true),
        Pal("guochao", "国潮 · 描金", "chinese", true, 0xFF2D1C14.toInt(), 0xFF3E2D26.toInt(), 0xFFD09579.toInt(), 0xFFFAF2EE.toInt(), 0xFFB1988D.toInt(), 0xFF3E2D26.toInt(), 0xFFFAF2EE.toInt(), 0xFFB1988D.toInt(), 0xFFE0B027.toInt(), 0xFF26120C.toInt(), 0xFF563F35.toInt(), 12, 12, 0, true),

    )

    /**
     * ⑭ 色彩派生 · 定稿（2026-10-05，《夕汀前端规范》14.3 / 14.4）——
     * **中国传统色 · 浅色 26 套**的「生成色 / 球色 / 生成字色」。key = palette id。
     * - genText = 0 → 用该主题自己的 `text` 槽；否则用给定字色（白字 `#FFFFFF` 或竹青的深字）。
     * - spark / sparkBg = 0 → 不特殊处理，沿用 accent（目前只有竹青给了专属值）。
     * - 底 `bg`、「设置」按钮：按规范**一律不改**（唯 竹青 按 14.3 把底改为 `#DCEDCF`）。
     */
    data class Deriv(
        val gen: Int,
        val ball: Int,
        val genText: Int = 0,
        val spark: Int = 0,
        val sparkBg: Int = 0
    )

    /** 14.4 逐套处方（14.3 竹青单独定稿）。白字写 0xFFFFFFFF；深字写 0（= 用该主题 text 槽） */
    val derive: Map<String, Deriv> = mapOf(
        "song" to Deriv(0xFF86CBD5.toInt(), 0xFFB4DFE4.toInt()),
        "tea" to Deriv(0xFF8FC464.toInt(), 0xFFB5CEA1.toInt(), 0xFF2F4526.toInt(), 0xFF778D61.toInt(), 0xFFDCE2D4.toInt()),
        "ruyao_tianqing" to Deriv(0xFF86C9D5.toInt(), 0xFFB4DDE4.toInt()),
        "longquan_fenqing" to Deriv(0xFF83D8A9.toInt(), 0xFFB1E7C9.toInt()),
        "longquan_meiziqing" to Deriv(0xFF83D89F.toInt(), 0xFFB1E7C3.toInt()),
        "song_yingqing" to Deriv(0xFF6E8DCC.toInt(), 0xFF9BB1DC.toInt(), 0xFFFFFFFF.toInt()),
        "dingyao_yabai" to Deriv(0xFFDDB77E.toInt(), 0xFFEAD2AE.toInt()),
        "jun_tianlan" to Deriv(0xFF6291C8.toInt(), 0xFF8FB0D8.toInt(), 0xFFFFFFFF.toInt()),
        "jiangdou_hong" to Deriv(0xFFD86D78.toInt(), 0xFFE59DA5.toInt(), 0xFFFFFFFF.toInt()),
        "yanzhi_shui" to Deriv(0xFFD86E6E.toInt(), 0xFFE59E9E.toInt(), 0xFFFFFFFF.toInt()),
        "ming_tianbai" to Deriv(0xFF6490C9.toInt(), 0xFF92B0D9.toInt(), 0xFFFFFFFF.toInt()),
        "qing_shanyuhuang" to Deriv(0xFFDDB87E.toInt(), 0xFFEAD3AE.toInt()),
        "tang_zhehuang" to Deriv(0xFFDDBE7E.toInt(), 0xFFEAD6AE.toInt()),
        "yuebai" to Deriv(0xFF698FCA.toInt(), 0xFF96B0DA.toInt(), 0xFFFFFFFF.toInt()),
        "shilv" to Deriv(0xFF86D5D4.toInt(), 0xFFB4E4E4.toInt()),
        "ouhe" to Deriv(0xFFD86A87.toInt(), 0xFFE49BAE.toInt(), 0xFFFFFFFF.toInt()),
        "taohong" to Deriv(0xFFD86B85.toInt(), 0xFFE59CAD.toInt(), 0xFFFFFFFF.toInt()),
        "haitang_hong" to Deriv(0xFFD86D7B.toInt(), 0xFFE59DA6.toInt(), 0xFFFFFFFF.toInt()),
        "zhusha" to Deriv(0xFFD0754F.toInt(), 0xFFDD9B7F.toInt(), 0xFFFFFFFF.toInt()),
        "xiangse" to Deriv(0xFFDDC57E.toInt(), 0xFFEADBAE.toInt()),
        "chijin" to Deriv(0xFFDDC07E.toInt(), 0xFFEAD8AE.toInt()),
        "tanse" to Deriv(0xFFD57260.toInt(), 0xFFE29D91.toInt(), 0xFFFFFFFF.toInt()),
        "wanse" to Deriv(0xFFD37458.toInt(), 0xFFE09C89.toInt(), 0xFFFFFFFF.toInt()),
        "hupo" to Deriv(0xFFCC793E.toInt(), 0xFFD99A6E.toInt(), 0xFFFFFFFF.toInt()),
        "yanxia" to Deriv(0xFFD86B81.toInt(), 0xFFE59CAA.toInt(), 0xFFFFFFFF.toInt()),
        "ailv" to Deriv(0xFF99D883.toInt(), 0xFFBFE7B1.toInt())
    )

    fun derivOf(id: String?): Deriv? = if (id.isNullOrEmpty()) null else derive[id]

    fun byId(id: String?): Pal? = if (id.isNullOrEmpty()) null else all.firstOrNull { it.id == id }
}
