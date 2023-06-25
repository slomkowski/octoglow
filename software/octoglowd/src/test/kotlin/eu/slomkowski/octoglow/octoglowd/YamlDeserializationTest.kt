package eu.slomkowski.octoglow.octoglowd

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mu.KLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull



class YamlDeserializationTest {
    companion object : KLogging()


    @Serializable
    data class ConfigDto(
        val i2cBus: Int,

        @SerialName("network-info")
        val networkInfo: NetworkInfo,
    ) {
        @Serializable
        data class NetworkInfo(
            val pingAddress : String,
        )
    }

    class ListDecoder(val list:MutableList<Map.Entry<String, Any>>) : AbstractDecoder() {
        private var elementIndex = 0

        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun decodeValue(): Any {
            return list.removeFirst().value
        }

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
            return elementIndex++
        }

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = ListDecoder(ArrayList(list))
    }


    fun <T> decodeFromHashMap(deserializer: DeserializationStrategy<T>, data: Map<String, Any>): T {
        val decoder = ListDecoder(data.entries.toMutableList())
        return decoder.decodeSerializableValue(deserializer)
    }

    @Test
    fun testDeserialization() {


        val yamlSettings = LoadSettings.builder().build()
        val yamlLoader = Load(yamlSettings)
        val rulesYaml = yamlLoader.loadFromReader(Files.newBufferedReader(Paths.get("config.yml")))
        assertThat(rulesYaml).isNotNull()
        val dto = decodeFromHashMap(ConfigDto.serializer(), rulesYaml as LinkedHashMap<String, Any>)
        assertNotNull(dto)
        logger.info { dto   }
    }
}