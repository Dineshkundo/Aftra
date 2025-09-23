{{- define "cloudgateway.fullname" -}}
{{ .Release.Name }}-{{ .Chart.Name }}
{{- end -}}
