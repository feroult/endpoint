package ${package}.utils

import io.yawp.testing.EndpointTestCaseBase

class EndpointTestCase : EndpointTestCaseBase() {

    override fun getAppPackage(): String {
        return "${package}"
    }

}
