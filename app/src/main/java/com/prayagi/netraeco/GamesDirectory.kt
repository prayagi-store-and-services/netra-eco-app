package com.prayagi.netraeco

/** Planned entries have no package, repository, APK or install action. Never imply release. */
object GamesDirectory {
    val planned = listOf(
        UpcomingApp("netra-ludo", "Netra Ludo", "Game · Planned", "Offline Ludo with 1-4 human seats and AI fill. Snakes & Ladders is planned inside the same app with a game switch. Easy / Medium / Hard AI for Ludo. Not released.", null),
        UpcomingApp("netra-chess", "Netra Chess", "Game · Planned", "Same-device two-player chess or on-device AI at Easy / Medium / Hard. Not released.", null),
        UpcomingApp("netra-sudoku", "Netra Sudoku", "Puzzle game · Planned", "Separate Sudoku app with Easy / Medium / Hard puzzles. Not released.", null),
        UpcomingApp("netra-match3", "Match-3 game (name pending)", "Game · Planned", "Original match-3 puzzles with advancing levels. Weekly level additions are planned, not available yet. Not released.", null),
        UpcomingApp("netra-tictactoe", "Tic Tac Toe (confirmation pending)", "Game · Awaiting confirmation", "Proposed same-device two-player or on-device AI game. Scope confirmation pending; development has not started. Not released.", null)
    )
    fun isGame(app: CatalogApp) = AppCategory.GAMES in AppCategories.classify(app.name, app.type, app.summary, app.categories)
    fun plansNotInCatalog(rows: List<AppRow>): List<UpcomingApp> = planned.filter { plan -> rows.none { it.app.id == plan.id } }
}
