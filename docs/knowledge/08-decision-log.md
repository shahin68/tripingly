
| 2026-11-22 | **iOS pin fade back to Google's own (Shahin: new pins still blinked).** The Core Animation fade on `marker.layer` (946b1a36) drew each new pin in full for a frame before the fade started from zero. New pins use `appearAnimation = .fadeIn` again, but only when they arrive clear; a pin that arrives dimmed shows at once with its dim. iOS only. | Shahin |
