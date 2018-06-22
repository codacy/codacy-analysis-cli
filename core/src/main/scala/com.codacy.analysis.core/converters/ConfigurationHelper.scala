package com.codacy.analysis.core.converters

import com.codacy.analysis.core.clients.api.{ToolParameter, ToolPattern}
import com.codacy.analysis.core.model.{Parameter, Pattern}

object ConfigurationHelper {

  implicit def apiParameterToInternalParameter(parameter: ToolParameter): Parameter =
    Parameter(parameter.name, parameter.value)

  implicit def apiPatternToInternalPattern(pattern: ToolPattern): Pattern =
    Pattern(pattern.internalId, pattern.parameters.map(apiParameterToInternalParameter))

}
