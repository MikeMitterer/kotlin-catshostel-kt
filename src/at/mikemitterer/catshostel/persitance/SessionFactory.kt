package at.mikemitterer.catshostel.persitance

import org.apache.ibatis.io.Resources
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder

/**
 * Erstellt eine SessionFactor zur Verwendung mit der Datenbank
 * 
 * Usage:
 *      val sqlSessionFactory = SessionFactory.sqlSessionFactory
 *
 * @since   09.04.20, 09:39
 */
object SessionFactory {
    val sqlSessionFactory: SqlSessionFactory

    init {
        val resource = "mybatis-config.xml";
        val inputStream = Resources.getResourceAsStream(resource);

        val reader = Resources.getResourceAsReader(resource)
        sqlSessionFactory = SqlSessionFactoryBuilder().build(reader, "development")
    }
}