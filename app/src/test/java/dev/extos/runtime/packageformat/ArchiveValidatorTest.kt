package dev.extos.runtime.packageformat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveValidatorTest {
    @Test
    fun readsValidArchive() {
        val archive = ArchiveValidator().read(zipOf("manifest.json" to "{}".toByteArray()))
        assertArrayEquals("{}".toByteArray(), archive.requireFile("manifest.json"))
    }

    @Test
    fun rejectsTraversal() {
        assertThrows(InvalidPackageException::class.java) {
            ArchiveValidator().read(zipOf("../manifest.json" to byteArrayOf(1)))
        }
    }

    @Test
    fun rejectsCaseCollisions() {
        assertThrows(InvalidPackageException::class.java) {
            ArchiveValidator().read(
                zipOf("icon.png" to byteArrayOf(1), "ICON.PNG" to byteArrayOf(2)),
            )
        }
    }

    @Test
    fun acceptsExplicitDirectoryEntries() {
        val archive = ArchiveValidator().read(
            zipOf("dist/" to null, "dist/index.html" to byteArrayOf(1)),
        )
        assertArrayEquals(byteArrayOf(1), archive.requireFile("dist/index.html"))
    }

    @Test
    fun rejectsFileUsedAsParentDirectory() {
        assertThrows(InvalidPackageException::class.java) {
            ArchiveValidator().read(
                zipOf("dist" to byteArrayOf(1), "dist/index.html" to byteArrayOf(2)),
            )
        }
    }

    @Test
    fun rejectsExpandedSizeOverLimit() {
        val validator = ArchiveValidator(ArchiveLimits(maxFileBytes = 3, maxTotalBytes = 3))
        assertThrows(InvalidPackageException::class.java) {
            validator.read(zipOf("large.bin" to byteArrayOf(1, 2, 3, 4)))
        }
    }

    private fun zipOf(vararg files: Pair<String, ByteArray?>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                if (content != null) zip.write(content)
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
