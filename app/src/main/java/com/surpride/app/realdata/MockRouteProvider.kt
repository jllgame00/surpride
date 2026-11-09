package com.surpride.app.realdata

/** Synthetic contract fixture, no geographic/traffic calculation. Public UI must always say MOCK. */
class MockRouteProvider(private val clock:()->Long):RouteProvider {
    override fun route(origin:GeoPoint,destination:GeoPoint):RouteReply {
        val seconds=if(origin==SeoulPilotCatalog.developmentOrigin) 540 else 780
        val fixture="""{"routes":[{"result_code":0,"summary":{"duration":$seconds,"distance":4200}}]}"""
        return KakaoRouteParser.parse(200,fixture,RouteOrigin.MOCK,clock())
    }
}
