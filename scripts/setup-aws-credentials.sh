#!/usr/bin/env bash

set -e

mkdir -p ~/.aws && touch ~/.aws/credentials

cat >> ~/.aws/credentials << EOF
[default]
aws_access_key_id=$ACCESS_KEY_ID
aws_secret_access_key=$SECRET_ACCESS_KEY
[shared-services]
source_profile = default
role_arn = arn:aws:iam::$AWS_ACCOUNT_ID_SHARED_SERVICES:role/CredentialsBucketReader
EOF
