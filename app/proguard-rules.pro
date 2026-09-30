# 玲珑调音 · PrimaTune — R8 规则
#
# 说明：
# - 布局 XML 里引用的自定义 View（PressButton / ThemeIndicatorView / ThemePreviewView 等）
#   由 AGP 自动生成的 aapt keep 规则保留，这里无需再声明。
# - 数据模型（Take / CustomVoice）用手写 org.json 序列化，没有反射，无需 keep。
# - okhttp 自带 consumer 规则；下面两条只是压掉多余告警。
-dontwarn okhttp3.**
-dontwarn okio.**
