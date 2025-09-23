{{- define "mailservice.name" -}}
{{ .Chart.Name }}
{{- end }}

{{- define "mailservice.fullname" -}}
{{ .Release.Name }}-{{ .Chart.Name }}
{{- end }}
