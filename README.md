# KitaTrack

**KitaTrack** is an offline-first Android personal finance tracker designed to clearly show how much money is actually safe to spend.

Instead of treating every peso in an account as available money, KitaTrack separates **Safe to Spend** funds from money reserved for debts, savings goals, and subscriptions.

> Your total money is not always your spendable money.

---

## Features

### Money Tracking

* Record income and expenses
* Separate income sources from expense categories
* View your current Safe-to-Spend balance
* Review transaction history
* Filter transactions by type and period
* Add optional notes to expenses

### Financial Plans

* Create category-based budgets
* Track Piggy Bank savings goals
* Manage debts you owe and money owed to you
* Track recurring subscriptions
* Reserve money automatically for upcoming obligations
* View progress, remaining amounts, due dates, and payment details

### Smart Allocation

When income is added, KitaTrack can allocate money using this priority:

1. Debts
2. Essential and high-priority subscriptions
3. Piggy Bank savings goals
4. Medium and low-priority subscriptions
5. Safe-to-Spend balance

Reserve allocations are not counted as expenses.

### Additional Tools

* Home-screen quick-add widget
* Local reminders
* Monthly summaries
* Rule-based financial insights
* CSV export
* JSON backup and restore
* Light and dark themes
* Fully offline storage

---

## Safe-to-Spend System

KitaTrack separates tracked money into four parts:

| Balance                  | Purpose                            |
| ------------------------ | ---------------------------------- |
| **Safe to Spend**        | Money that can be freely used      |
| **Debt Reserve**         | Money reserved for debt payments   |
| **Piggy Bank Total**     | Money locked into savings goals    |
| **Subscription Reserve** | Money reserved for recurring bills |

```text
Total Money Tracked =
Safe to Spend
+ Debt Reserve
+ Piggy Bank Total
+ Subscription Reserve
```

Only the **Safe-to-Spend** balance is presented as freely available money.

---

## Screenshots

Add your screenshots to an `assets/screenshots` folder and update the paths below.

<p align="center">
  <img src="assets/screenshots/dashboard.png" width="240" alt="KitaTrack Dashboard">
  <img src="assets/screenshots/history.png" width="240" alt="KitaTrack History">
  <img src="assets/screenshots/plans.png" width="240" alt="KitaTrack Plans">
</p>

<p align="center">
  <img src="assets/screenshots/add-transaction.png" width="240" alt="Add Transaction">
  <img src="assets/screenshots/settings.png" width="240" alt="KitaTrack Settings">
  <img src="assets/screenshots/widget.png" width="240" alt="KitaTrack Home Screen Widget">
</p>

---

## Technology

* Kotlin
* Android XML layouts
* MVVM architecture
* Repository pattern
* Room Database
* Kotlin Coroutines and Flow
* Material-style interface
* Android App Widgets
* Local notifications

KitaTrack does not require a backend server, cloud account, or internet connection for its core features.

---

## Privacy

KitaTrack is designed to keep financial information on the user's device.

* No online account is required
* No backend server is used
* Core financial data is stored locally
* Backups are exported manually as JSON
* Spreadsheet-friendly records can be exported as CSV

Users are responsible for securely storing exported backup files.

---

## Installation

### Download the APK

Download the latest APK from the repository's **Releases** section.

1. Download the APK file.
2. Allow installation from unknown sources when prompted.
3. Open the APK and install KitaTrack.
4. Launch the app and begin recording transactions.

Android may display a warning because the APK is installed outside the Google Play Store.

### Build from Source

1. Clone the repository:

```bash
git clone https://github.com/YOUR_USERNAME/KitaTrack.git
```

2. Open the project in Android Studio.

3. Allow Gradle to synchronize the project.

4. Build and install the debug version:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The generated debug APK is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Backup and Restore

### JSON Backup

JSON backup preserves the app's supported financial records and settings for restoration inside KitaTrack.

Use JSON when:

* Moving data to another device
* Reinstalling the application
* Keeping a full local backup

### CSV Export

CSV export is intended for viewing and analyzing transaction records in spreadsheet applications.

Use CSV when:

* Opening records in Excel or Google Sheets
* Creating external reports
* Reviewing transactions outside the app

CSV export is not a replacement for a complete JSON backup.

---

## Current Development Status

KitaTrack is actively being developed.

The current public APK focuses on the stable core experience:

* Dashboard
* Income and expense tracking
* Transaction history
* Budgets
* Piggy Banks
* Debts
* Subscriptions
* Categories and income sources
* Settings, export, and backup
* Home-screen widget

The Reports section is temporarily hidden from the public APK while it is being improved. Its implementation remains part of the project and may return in a future release.

---

## Project Principles

KitaTrack follows several core rules:

* Spendable and reserved money must remain separate
* Reserved money must never be presented as freely spendable
* Reserve allocations are not expenses
* Payments from reserves must not subtract twice
* Budget usage is based on actual expenses
* Piggy Bank completion is not income or expense
* Financial calculations should remain testable and outside the UI where practical
* Core functionality should remain available offline

---

## Roadmap

Planned improvements may include:

* Completed Reports and chart screens
* More detailed monthly summaries
* Improved budget analysis
* Additional widget sizes
* Better backup management
* More reminder options
* Further accessibility and interface improvements

The roadmap may change as the project develops.

---

## Contributing

KitaTrack is currently a personal project, but suggestions and issue reports are welcome.

When reporting a problem, include:

* Android version
* Device or emulator model
* Steps to reproduce the issue
* Expected behavior
* Actual behavior
* Screenshots or logs when available

Do not include private financial data in public issues.

---

## Development Note

This project was developed with assistance from AI coding tools. All generated and suggested changes are reviewed, integrated, tested, and adjusted according to the project's requirements and financial rules.

---

## License

No license has been selected yet.

Until a license is added, the source code remains under standard copyright protection and may not automatically be copied, modified, or redistributed.

---

## Author

Developed by **Kevin Kyle S. Alfon**

* GitHub: [KKFonsi](https://github.com/KKFonsi)
* Portfolio: [alfon-portfolio.vercel.app](https://alfon-portfolio.vercel.app/)
* LinkedIn: [Alfon Kevin Kyle](https://www.linkedin.com/in/alfon-kevin-kyle-794634411/)
