package at.mikemitterer.catshostel.persitance

import at.mikemitterer.catshostel.model.Cat
import at.mikemitterer.catshostel.persitance.mapper.CatsMapper
import org.apache.ibatis.annotations.*
import org.apache.ibatis.session.SqlSessionFactory

/**
 * Verwendet das selbe Interface wie CatsMapper - kann aber später einfach geändert werden
 */
interface CatDAO {
    val numberOfCats: Long
    val cats: List<Cat>
    suspend fun cat(id: Number): Cat
    suspend fun insert(cat: Cat)
    suspend fun update(cat: Cat)
    suspend fun delete(cat: Cat)
    suspend fun deleteAll()
}

class CatDAOImpl(
        private val sessionFactory: SqlSessionFactory) : CatDAO {
    
    override suspend fun insert(cat: Cat) {
        sessionFactory.openSession().use {
            it.getMapper(CatsMapper::class.java).insert(cat)
            it.commit()
        }
    }

    override val cats: List<Cat>
        get() {
            sessionFactory.openSession().use { session ->
                return session.getMapper(CatsMapper::class.java).cats
            }
    }

    override suspend fun cat(id: Number): Cat {
        sessionFactory.openSession().use { session ->
            return session.getMapper(CatsMapper::class.java).cat(id)
        }
    }

    override val numberOfCats: Long
        get() {
        sessionFactory.openSession().use { session ->
            return session.getMapper(CatsMapper::class.java).numberOfCats
        }
    }

    override suspend fun update(cat: Cat) {
        sessionFactory.openSession().use { session ->
            session.getMapper(CatsMapper::class.java).update(cat)
            session.commit()
        }
    }

    override suspend fun delete(cat: Cat) {
        sessionFactory.openSession().use { session ->
            session.getMapper(CatsMapper::class.java).delete(cat)
            session.commit()
        }
    }

    override suspend fun deleteAll() {
        sessionFactory.openSession().use {
            it.getMapper(CatsMapper::class.java).deleteAll()
            it.commit()
        }
    }
}