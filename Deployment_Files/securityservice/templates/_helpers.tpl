{{- define "securityservice.fullname" -}}
{{ .Chart.Name }}
{{- end }}

{{- define "securityservice.labels" -}}
app: {{ include "securityservice.fullname" . }}
{{- end }}
