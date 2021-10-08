package org.rdtoolkit.model.diagnostics

import org.rdtoolkit.support.model.session.TestSession

interface RdtDiagnosticProfile {
    fun id() : String
    fun readableName() : String
    fun resourceId(): String?
    fun timeToResolve() : Int
    fun timeToExpire() : Int
    fun resultProfiles() : Collection<ResultProfile>
    fun tags() : Collection<String>

    fun isResultSetComplete(result : TestSession.TestResult) : Boolean {
        val results = result.results
        for (profile in resultProfiles()) {
            if (!results.containsKey(profile.id())) {
                return false;
            }
            val selectedOutcome = results.get(profile.id())
            var validAnswer = false
            for (outcome in profile.outcomes()) {
                if (outcome.id() == selectedOutcome) {
                    validAnswer = true
                }
            }
            if (!validAnswer) {
                return false
            }
        }
        return true
    }
}

interface DiagnosticOutcome {
    fun id(): String
    fun readableName(): String
}

interface ResultProfile {
    fun id() : String
    fun readableName() : String
    fun outcomes() : Collection<DiagnosticOutcome>
}

data class ConcreteDiagnosticOutcome ( var id : String,
                                       var readableName: String
) : DiagnosticOutcome {
    override fun id(): String {
        return id;
    }

    override fun readableName(): String {
        return readableName;
    }
}

data class ConcreteResultProfile (  var id : String,
                                    var readableName: String,
                                    var resultProfiles: Collection<DiagnosticOutcome>
) : ResultProfile {
    override fun id(): String {
        return id;
    }

    override fun readableName(): String {
        return readableName;
    }

    override fun outcomes(): Collection<DiagnosticOutcome> {
        return resultProfiles
    }

}

data class ConcreteProfile ( var id : String,
                             var readableName: String,
                             var resourceId: String?,
                             var timeToResolve: Int,
                             var timeToExpire: Int,
                             var resultProfiles: Collection<ResultProfile>,
                             var tags: Collection<String>

) : RdtDiagnosticProfile {
    override fun id(): String {
        return id;
    }

    override fun resourceId(): String? {
        return resourceId
    }

    override fun readableName(): String {
        return readableName;
    }

    override fun timeToResolve(): Int {
        return timeToResolve
    }

    override fun timeToExpire(): Int {
        return timeToExpire
    }

    override fun resultProfiles(): Collection<ResultProfile> {
        return resultProfiles
    }

    override fun tags() : Collection<String> {
        return tags
    }
}
