{{/*
_helpers.tpl — reusable named templates shared across all chart templates.
Referenced with {{ include "rmn-insights-api.<name>" . }} in other files.
*/}}

{{/*
rmn-insights-api.name
Returns the chart name, truncated to 63 characters (Kubernetes label value limit).
*/}}
{{- define "rmn-insights-api.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
rmn-insights-api.fullname
Combines release name and chart name to produce a unique resource name.
If the release name already contains the chart name (e.g. helm install rmn-insights-api ./helm)
the chart name is not appended again, preventing double-barrelled names like
"rmn-insights-api-rmn-insights-api". Truncated to 63 characters (Kubernetes limit).
*/}}
{{- define "rmn-insights-api.fullname" -}}
{{- if contains .Chart.Name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
rmn-insights-api.labels
Standard Kubernetes recommended labels applied to every resource.
These enable consistent filtering, monitoring, and tooling integration.
*/}}
{{- define "rmn-insights-api.labels" -}}
app.kubernetes.io/name: {{ include "rmn-insights-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
