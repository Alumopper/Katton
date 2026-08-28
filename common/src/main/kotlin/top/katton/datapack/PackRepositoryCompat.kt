package top.katton.datapack

/** Bridges Mojang's one-argument API and Paper's required-pack-aware overload. */
internal object PackRepositoryCompat {
    fun setSelected(repository: Any, selectedIds: Collection<String>) {
        val methods = repository.javaClass.methods.filter { method ->
            method.name == "setSelected" &&
                method.parameterTypes.firstOrNull()?.let(Collection::class.java::isAssignableFrom) == true
        }
        val paperMethod = methods.firstOrNull { method ->
            method.parameterCount == 2 && method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        if (paperMethod != null) {
            paperMethod.invoke(repository, selectedIds, true)
            return
        }

        val vanillaMethod = methods.firstOrNull { it.parameterCount == 1 }
            ?: error("Unsupported PackRepository.setSelected signature on ${repository.javaClass.name}")
        vanillaMethod.invoke(repository, selectedIds)
    }
}
