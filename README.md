# CarryPick — MVP prototype

Traveler-linked overseas purchase platform (Ajou Univ. Capstone, Team 14).

```
carrypick/
├── backend/    Spring Boot 3.5 REST API (Java 21, JPA, Spring Security + JWT)
└── frontend/   React 18 + Vite + TypeScript (mobile-first web app)
```

## Run it locally

You need **Java 21**, **Maven 3.9+** (or IntelliJ, which bundles it) and **Node 20+**.

**1. Start the API** (port 8080)

```bash
cd backend
mvn spring-boot:run
```

By default it uses an in-memory H2 database and loads demo data, so no setup is needed. All data resets when the server restarts. The API docs are at <http://localhost:8080/swagger-ui.html>.

**2. Start the web app** (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Use your browser's phone view (DevTools device toolbar) to see the mobile layout. Vite forwards `/api` and `/uploads` to port 8080.

**3. Log in with a test account.** The password for all of them is `carrypick123`.

| Email | Role | Has |
| --- | --- | --- |
| buyer@carrypick.test | Buyer | 2 open requests |
| buyer2@carrypick.test | Buyer | 1 open request |
| traveler@carrypick.test | Traveler | Trips to Tokyo, Osaka and New York, 7 offers |
| traveler2@carrypick.test | Traveler | Trips to Paris and Bangkok, 5 offers |

The login screen has one-tap buttons for the first buyer and traveler accounts. Each browser tab keeps its own login, so you can be the buyer in one tab and the traveler in another tab at the same time. Closing a tab logs that tab out.

**Language.** The app opens in Korean. Switch to English with the 한국어 / EN toggle on the login screen or on My page. The choice is saved in the browser. All text lives in `frontend/src/i18n.tsx`; the Korean dictionary is typed against the English one, so a missing translation is a compile error. Notifications are stored as a kind plus names and amounts, so each person reads them in their own language.

### If port 8080 is already in use

An error like "No valid crumb was included in the request" means another program (usually Jenkins) is answering on port 8080 instead of CarryPick. Either stop that program, or run CarryPick's API on another port:

```bash
# terminal 1
cd backend
PORT=8081 mvn spring-boot:run          # Windows PowerShell: $env:PORT="8081"; mvn spring-boot:run

# terminal 2
cd frontend
API_PORT=8081 npm run dev              # Windows PowerShell: $env:API_PORT="8081"; npm run dev
```

## Demo script (the two flows in the plan)

**Flow A — book an existing offer**

1. Buyer: Home → tap the **TYO** row on the departures board → *Tokyo Banana* → Book → Pay → *Approve payment*.
2. Traveler: Orders → the order → *I bought it* → attach a receipt photo → *Read receipt with AI*. The won amount is filled in; check it and save.
3. Traveler: *Mark as handed over*.
4. Buyer: open the order → *I received it*. The order is settled (simulated).

**Flow B — request accepted, receipt over budget**

1. Buyer: *Can't find it?* → Japan, budget ₩30,000 → Post.
2. Traveler: Requests → the request → Accept.
3. Buyer: open the order → Pay. Try *Simulate declined card* first to show a payment failure, then retry.
4. Traveler: *I bought it* with a price above ₩30,000.
5. Buyer: the over-budget prompt appears → *Approve and pay* the extra.
6. Traveler hands over, buyer confirms, and the order is settled.

## Payments

| Mode | How | Notes |
| --- | --- | --- |
| `mock` (default) | Built-in fake card sheet | Works offline. A "declined card" button tests the failure path. |
| `toss` | Toss Payments **test** payment window | Set the test keys below. No real money is charged. |

To use Toss test mode, start the API with:

```bash
PAYMENT_MODE=toss TOSS_CLIENT_KEY=test_ck_xxx TOSS_SECRET_KEY=test_sk_xxx mvn spring-boot:run
```

- Get the keys from the Toss Payments developer center. The browser uses `payment().requestPayment()` from the v2 SDK, which takes `test_ck_` keys.
- Only the server knows the secret key. It decides the amount when the payment starts and checks it again before confirming with Toss, so a tampered amount from the browser is rejected.
- Toss redirects back to `/pay/success` or `/pay/fail`.
- Toss mode was written against the Toss docs but could not be run in the environment this was built in. Test it once before the demo.

## AI receipt reader

When the traveler uploads a receipt, *Read receipt with AI* sends the photo to the server, which asks Claude (vision) to read the store, items, currency and total. The server converts the total to won with a fixed rate table and flags items that customs limits apply to (alcohol, tobacco, health supplements, medicine, meat). The result only fills in the price field. The traveler still checks it and presses Save, because a model can misread a photo.

| Mode | How | Notes |
| --- | --- | --- |
| `mock` (default) | Returns a fixed sample receipt (Tokyo Banana, ¥2,592) | Works offline, no key. The screen says it is a sample. |
| `anthropic` | Calls the Claude Messages API | Needs an Anthropic API key. |

```bash
AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-xxx mvn spring-boot:run
# optional: AI_MODEL=claude-sonnet-5-5   (default claude-haiku-5-5, the cheapest)
```

- The key is only read by the server (`app.ai` in `application.yml`). Never put it in the React app or commit it to GitHub.
- Endpoint: `POST /api/receipts/scan` (multipart field `image`, JPG/PNG/WEBP/GIF, 5 MB max, login required).
- Exchange rates are fixed demo values under `app.fx` in `application.yml`. Update them before a demo; they are labelled as fixed rates in the app.
- The customs notes are short reminders, not legal advice. The 관세청 site has the current rules.
- The real API call was written against the Anthropic docs but could not be run in the environment this was built in. Test it once with your key.

## Use PostgreSQL instead of H2

```bash
createdb carrypick   # user and password carrypick/carrypick, or set DB_USER / DB_PASSWORD
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The tables are created automatically, and demo data is only loaded when the `users` table is empty.

## Tests

```bash
cd backend && mvn test
```

`OrderFlowTest` runs Flow A, Flow B with over-budget approval, payment failure and retry, and the security rules (strangers can't see orders, the wrong role gets 403, tampered amounts are rejected, no token gets 401).

## Where things are

| What | File |
| --- | --- |
| Order status rules | `backend/.../service/OrderService.java` |
| Payment prepare / confirm / fail | `backend/.../service/PaymentService.java`, `TossClient.java` |
| Pricing formula (B + R + F + T) | `backend/.../service/PricingService.java` (fee 10%, tax estimate above ₩150,000 — change in `application.yml`) |
| Who can call what | `backend/.../config/SecurityConfig.java` and the checks in `OrderService` |
| Demo data | `backend/.../config/DataSeeder.java` |
| API endpoints | `backend/.../web/*Controller.java` |
| Departures board (B1) | `GET /api/board` in `MarketController`, `DeparturesBoard` in `frontend/src/components/cards.tsx` |
| AI receipt reader | `backend/.../service/ReceiptScanService.java`, `frontend/src/components/receipt.tsx` |
| Screens (C1–C3, B1–B8, T1–T7) | `frontend/src/pages/` — the route map is at the top of `App.tsx` |
| Korean / English text | `frontend/src/i18n.tsx` |
| Colors, spacing, fonts | `frontend/src/styles.css` (tokens at the top) |
| API client and types | `frontend/src/api.ts` |

## Design notes

The screens follow the team's Figma Make design ("carry."):

- **Colors:** dark green `#17231D` and lime `#C7FF2D` on an off-white page `#F6F7F1`. These are copied from the Figma file's styles.
- **Type:** serif display headings (Georgia, with Noto Serif KR for Korean) and Inter for text (IBM Plex Sans KR for Korean). Fonts are bundled through npm (`@fontsource`), so they work offline.
- **Buyer home:** photo hero with search, "Shop the world" country cards, "Most wanted right now" ranking cards, live traveler offers, and the lime "Tell travelers what you need" panel.
- **Other screens:** dashboard heroes for buyer and traveler with stat cards and tabs, offer cards with Price / Reward / Arrives, the payment summary card, and a bottom sheet for forms.
- **Bottom navigation:** includes a Travel mode / Buyer mode switch, as in the design.
- **Sparkle trail:** lime sparkles follow the mouse, from the Figma design. It's off on touch screens and when reduced motion is on (`components/SparkleTrail.tsx`).
- **Photos:** the photos in `frontend/src/assets/` were taken from the Figma design. When a traveler hasn't uploaded a photo, snacks/food, beauty and "other" items show a category photo, and other categories show an icon.
- **Brand:** the wordmark reads `carrypick.`; change it in `Wordmark` in `components/Layout.tsx`.

## Known simplifications

- All prices are in KRW. The traveler enters the converted price, or lets the AI receipt reader estimate it with the fixed rate table.
- Uploaded images are saved to `backend/uploads/` on local disk.
- Settlement runs immediately when the buyer confirms receipt, and it is simulated. There is no admin screen (A1).
- Chat (C4), reviews (B9), a separate offer list (T4) and an earnings page (T8) are not built. T1 shows the earnings totals.
- The popular ranking counts paid orders from the last 30 days. Seeded orders feed it, which is why it is labelled "Demo data".
