# 四端 UI 设计规范（waimai-web）

> 建立于 2026-10-04。**改样式前先读这里**；token 定义在 `apps/user-h5/src/style.css`，其余端照此映射。
> 依据：refactoring-ui（层次与间距尺度）、Vant 4 官方主题定制、impeccable craft-floor（对比度/阴影/图标底线）。

---

## 1. 设计原则

1. **移动优先、卡片化**：内容按区块包成卡片，页面底色统一为浅灰，卡片浮在其上。
2. **暖橙品牌**：主色只用于「价格、评分、主按钮、选中态、强调信息」，不做大面积铺色。
3. **克制**：明确不用四样东西 —— 玻璃模糊/backdrop-filter 装饰、渐变文字、卡片套卡片、零偏移的彩色光晕阴影。这几样是"AI 生成味"的典型默认做法。
4. **图标一律用组件库图标**（`<van-icon>`），**禁止 emoji 代替图标系统**。
5. **浏览器表面也属于设计**：`theme-color`、焦点环、选中色、等宽数字都要交代。

---

## 2. 设计 token

定义位置：`apps/user-h5/src/style.css` 的 `:root`。组件内**只允许引用 `var(--wm-*)`**，不写字面值（白色文字 `#fff` 例外）。

### 品牌色阶
| token | 值 | 用途 |
|---|---|---|
| `--wm-primary` | `#ff6034` | 主色：价格、评分、主按钮、选中态 |
| `--wm-primary-dark` | `#e64d22` | 渐变深端、浅底上的强调文字 |
| `--wm-primary-light` | `#ff8a3d` | 渐变浅端 |
| `--wm-primary-50` | `#fff5f0` | 最浅底（公告、回复、提示块） |
| `--wm-primary-100` | `#ffe6da` | 浅底描边 |
| `--wm-primary-200` | `#ffcbb4` | 标签描边 |

### 中性色
| token | 值 | 用途 |
|---|---|---|
| `--wm-text-1` | `#1a1a1a` | 标题 |
| `--wm-text-2` | `#4d4d4d` | 正文 |
| `--wm-text-3` | `#737373` | 次要文字（白底约 4.6:1，达标） |
| `--wm-text-4` | `#a6a6a6` | 辅助/占位（不承载关键信息） |
| `--wm-bg-page` | `#f5f6f8` | 页面底色 |
| `--wm-bg-card` | `#ffffff` | 卡片底色 |
| `--wm-border` | `#ebedf0` | 分隔线、描边 |

### 语义色
`--wm-success` `#07c160` · `--wm-warning` `#ff976a` · `--wm-danger` `#ee0a24`

### 间距刻度（4 的倍数）
`--wm-space-1` 4px · `-2` 8px · `-3` 12px · `-4` 16px · `-5` 20px · `-6` 24px · `-8` 32px

### 圆角
`--wm-radius-sm` 6px · `-md` 10px · `-lg` 14px · `-xl` 20px · `-full` 999px

### 阴影（一律带偏移 + 柔和模糊）
`--wm-shadow-1` `0 1px 3px rgba(24,24,32,.06)` · `-2` `0 4px 12px rgba(24,24,32,.08)` · `-3` `0 8px 24px rgba(24,24,32,.12)` · `--wm-shadow-primary` `0 6px 16px rgba(255,96,52,.28)`

### 字号与行高
`--wm-font-xs` 11px · `-sm` 12px · `-md` 14px · `-lg` 16px · `-xl` 18px · `-2xl` 22px
`--wm-leading-tight` 1.25 · `--wm-leading-normal` 1.5

### 触控
`--wm-tap-min` 44px —— 可点击的行与主按钮的下限。

---

## 3. Vant 主题映射

`style.css` 里把 Vant 的主题变量指到 token 上，**改一行 token，全端组件跟随**：

```
--van-primary-color / --van-success-color / --van-danger-color / --van-warning-color
--van-text-color / --van-text-color-2 / --van-text-color-3
--van-background / --van-background-2 / --van-border-color
--van-radius-md / --van-radius-lg
--van-cell-font-size / --van-tabbar-item-active-color / --van-tabbar-item-font-size
--van-empty-description-color
```

注意：`main.ts` 里 `vant/lib/index.css` 必须**先**引入、`./style.css` **后**引入，覆盖才生效。

---

## 4. 组件规范

| 元素 | 规范 |
|---|---|
| 页面容器 | `min-height: 100vh; background: var(--wm-bg-page);` |
| 卡片 | `background: var(--wm-bg-card); border-radius: var(--wm-radius-lg); box-shadow: var(--wm-shadow-1); margin: var(--wm-space-3); padding: var(--wm-space-4);` |
| 区块标题 | `font-size: var(--wm-font-lg); font-weight: 600; color: var(--wm-text-1);` |
| 卡内列表行 | 用 `border-top: 1px solid var(--wm-border)` 分隔，首行去掉；不要用「卡中卡」 |
| 主按钮 | `min-height: var(--wm-tap-min)`；颜色跟随 `--van-primary-color`，**不要写 `color="#ff6034"`** |
| 底部固定栏 | `padding-bottom: calc(var(--wm-space-3) + env(safe-area-inset-bottom))`；页面内容区要留出等量 `padding-bottom` 防遮挡 |
| 会话气泡 | 自己：`--wm-primary` + 白字；对方：`--wm-bg-page` + `--wm-text-2`；圆角 `--wm-radius-lg` |
| 优惠券卡 | 金额用 `--wm-font-2xl` 加粗 + 内部 `¥`/`折` 降字号做层级；已用/过期整卡 `opacity: .6` 且色阶降到 `--wm-text-4` |
| 价格 | 统一用全局 `.price`（自带 `¥` 前缀 + `tabular-nums`），**不要再手写 `¥`** |

---

## 5. 硬约束与已知坑

1. **地图类 JS 参数不认 CSS 变量**。`AMap.Polyline` 的 `strokeColor`、Marker 颜色等直接进 canvas 绘图管线，必须用真实色值，并在注释里标注与 `--wm-primary` 的同步点。当前实例：`pages/Track.vue`。
2. **Vant `Dialog` 的 `confirmButtonColor` 走 inline style**（源码 `Dialog.mjs` 里 `color: props.confirmButtonColor`），因此传 `'var(--wm-danger)'` 是有效的，无需硬编码。
3. **带 tabbar 的页面高度必须是 `calc(100vh - 50px)`**（tabbar 占 50px），否则输入框会被压在 tabbar 下面点不到。当前实例：`pages/Chat.vue`。
4. **地图容器必须有明确高度**（当前 `60vh`），改自适应会塌陷。
5. **`.price` 自带 `¥`**：`<span class="price">¥{{x}}</span>` 会渲染成「¥¥」（历史上 `Cart.vue`、`MerchantDetail.vue` 都踩过，已修）。
6. scoped 样式要作用于子组件内部元素时必须用 `:deep()`。

---

## 6. 改造进度

| 范围 | 状态 |
|---|---|
| token 层（style.css）+ Vant 主题映射 | ✅ |
| Home.vue / MerchantDetail.vue（样板） | ✅ |
| 全项目硬编码色值 token 化（102 处 / 18 文件） | ✅ |
| Cart / Pay / Orders / OrderDetail / Review | ✅ |
| ImChat / ImSessions / MyTickets / Chat / Coupons / CouponHall / Track | ✅ |
| Login / Profile / Search / Address / Location / AddressEditor | 进行中 |
| rider-h5（同 Vant，可直接复用 token） | 待办 |
| merchant-web / admin-web（Element Plus，需 `--el-*` 映射） | 待办 |

---

## 7. 推广到其他端

- **rider-h5**：同为 Vant 4，把 `style.css` 换成本套 token（保留原有的映射段）即可。
- **merchant-web / admin-web**：Element Plus 的主题变量前缀是 `--el-*`，需要做一层映射（`--el-color-primary: var(--wm-primary)` 等），卡片与文字层级规范完全复用。
