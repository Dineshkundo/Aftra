
{{- define "fileingestion.name" -}}
{{- default .Chart.Name .Values.nameOverride -}}
{{- end -}}

{{- define "fileingestion.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "fileingestion.name" .) -}}
{{- end -}}