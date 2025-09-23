{{- define "codaui.name" -}}
{{ .Chart.Name }}
{{- end }}

{{- define "codaui.fullname" -}}
{{ .Release.Name }}-{{ .Chart.Name }}
{{- end }}
