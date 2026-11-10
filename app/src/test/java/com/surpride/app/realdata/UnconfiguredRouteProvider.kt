package com.surpride.app.realdata

class UnconfiguredRouteProvider:RouteProvider {
    override fun route(origin:GeoPoint,destination:GeoPoint)=RouteReply.Failure(RouteError.NOT_CONFIGURED)
}
