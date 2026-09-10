# LocTime

[日本語版 README](README.ja.md)

LocTime is an Android application that checks whether you are at a specified location at scheduled times and sends notifications accordingly.
It supports location- and time-based routines, such as "I want to be at my office 1 minute before work starts."

## Features

- **Location Management**: Register up to 10 locations.
- **Schedule Settings**: Set up to 10 scheduled times per location.
- **Custom Notifications**: Customize notification messages for each scheduled time.
- **Offset Adjustment**: Fine-tune trigger timing for each location (-30 minutes to +30 minutes).
  - Example: Set "-1 minute" for work to trigger the location check 1 minute prior to the scheduled time.
- **Real-time Status**: Visually check current distance, coordinates difference, and "In Range / Out of Range" status on the main list screen.
- **Auto Restore**: Automatically re-schedules alarms upon device reboot.

## How to Set Locations

| Method | Description |
|---|---|
| Address Search | Search by address using `Geocoder` |
| Get Current Location | Automatically fetch latitude & longitude via GPS |
| Manual Input | Manually enter latitude, longitude, and radius (m) |

## Workflow

1. At the scheduled "Time + Offset", a background alarm triggers.
2. At that precise moment, GPS location is retrieved to check if you are within the designated location radius.
3. If you are within range, a notification is displayed with your specified message.
4. Battery consumption is minimized because GPS is not constantly running in the background (time-triggered execution).

*Note: For optimal performance, location permission should be set to "Allow all the time" and battery usage to "Unrestricted".*

## Tech Stack

| Item | Details |
|---|---|
| Target OS | Android 8.0 (API 26) or higher |
| Language | Kotlin |
| Architecture | MVVM (ViewModel + Repository) |
| UI | Jetpack Compose / Material3 |
| Database | Room |
| Location | FusedLocationProviderClient (Google Play Services) |
| Async | Coroutines / Flow |
| Navigation | Navigation Compose |

## License

MIT License
