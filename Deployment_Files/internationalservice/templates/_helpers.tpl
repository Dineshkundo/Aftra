{{- define "internationalservice.name" -}}
{{ .Chart.Name }}
{{- end }}

{{- define "internationalservice.fullname" -}}
{{ .Release.Name }}-{{ .Chart.Name }}
{{- end }}
