package com.surpride.app.navigation

class FakeNavigationDataSource : NavigationDataSource {
    override val state = NavigationState(origin = NavigationOrigin.FAKE)
    private var observer: ((NavigationState) -> Unit)? = null
    override fun start(observer: (NavigationState) -> Unit) { this.observer = observer; observer(state) }
    override fun refresh() { observer?.invoke(state) }
    override fun requestTestRoute() = Unit
    override fun cancelTestRoute() = Unit
    override fun showNavigation() = Unit
    override fun queryHistory() = Unit
    override fun stop() { observer = null }
}
