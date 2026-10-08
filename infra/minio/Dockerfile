# The former quay.io/minio/minio image no longer permits anonymous pulls.
# Build the same pinned upstream release for local development and E2E.
FROM golang:1.24-alpine AS builder
ENV CGO_ENABLED=0
RUN go install github.com/minio/minio@RELEASE.2025-07-23T15-54-02Z

FROM alpine:3.22
RUN apk add --no-cache ca-certificates
COPY --from=builder /go/bin/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["/usr/local/bin/minio"]
CMD ["server", "/data", "--console-address", ":9001"]
