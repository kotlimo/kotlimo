package io.kotlimo.filesystem

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

class S3Filesystem(
    private val bucket: String,
    private val client: S3Client
) : Filesystem {
    override fun put(path: String, contents: String): Boolean = put(path, contents.toByteArray())

    override fun put(path: String, bytes: ByteArray): Boolean {
        client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(normalize(path)).build(),
            RequestBody.fromBytes(bytes)
        )
        return true
    }

    override fun get(path: String): String = getBytes(path).toString(Charsets.UTF_8)

    override fun getBytes(path: String): ByteArray =
        client.getObject(GetObjectRequest.builder().bucket(bucket).key(normalize(path)).build()).use { it.readAllBytes() }

    override fun exists(path: String): Boolean = try {
        client.headObject(HeadObjectRequest.builder().bucket(bucket).key(normalize(path)).build())
        true
    } catch (_: NoSuchKeyException) {
        false
    }

    override fun delete(path: String): Boolean {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(normalize(path)).build())
        return true
    }

    private fun normalize(path: String): String = path.trim().removePrefix("/")

    companion object {
        fun client(region: String, key: String, secret: String, endpoint: String? = null): S3Client {
            val builder = S3Client.builder()
                .region(Region.of(region.ifBlank { "us-east-1" }))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
            if (!endpoint.isNullOrBlank()) {
                builder.endpointOverride(URI.create(endpoint))
                builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            }
            return builder.build()
        }
    }
}
