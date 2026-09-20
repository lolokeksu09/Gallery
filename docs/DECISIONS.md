# Decisions

- Android13 minimum because this is a personal application for Android13; no legacy storage support.
- Keep a single module and explicit small dependencies; avoid framework overhead.
- DataStore favorites instead of Room for a small URI set; migrate if richer records become necessary.
- System deletion confirmation is mandatory and irreversible deletion should be treated carefully.
- Build on GitHub; runtime has no network permission. CI internet access is separate from application capabilities.
- A debug artifact is a test build. Release signing requires a persistent private key stored securely, not in Git.
