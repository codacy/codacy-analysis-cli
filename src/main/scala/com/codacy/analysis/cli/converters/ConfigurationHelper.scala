package com.codacy.analysis.cli.converters

import com.codacy.analysis.cli.clients.api.{ToolParameter, ToolPattern}
import com.codacy.analysis.cli.model.{Parameter, Pattern}

object ConfigurationHelper {

  implicit def apiParameterToInternalParameter(parameter: ToolParameter): Parameter =
    Parameter(parameter.name, parameter.value)

  implicit def apiPatternToInternalPattern(pattern: ToolPattern): Pattern =
    Pattern(pattern.internalId, pattern.parameters.map(apiParameterToInternalParameter))

}
