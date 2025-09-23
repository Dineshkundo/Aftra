{{- define "cacheservice.name" -}}
{{- default .Chart.Name .Values.nameOverride -}}
{{- end -}}

{{- define "cacheservice.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "cacheservice.name" .) -}}
{{- end -}}