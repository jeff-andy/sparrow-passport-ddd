package com.sparrow.passport.config;

import com.sparrow.passport.JwtFilter;
import com.sparrow.passport.infrastructure.services.AccountRealm;
import com.sparrow.passport.infrastructure.services.JwtRealm;
import com.sparrow.passport.infrastructure.support.shiro.JwtUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.Filter;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.crazycake.shiro.IRedisManager;
import org.crazycake.shiro.RedisCacheManager;
import org.crazycake.shiro.RedisManager;
import org.crazycake.shiro.RedisSessionDAO;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {

    @Bean JwtUtils jwtUtils() {
        return new JwtUtils();
    }

    @Bean
    public JwtFilter jwtFilter(JwtUtils jwtUtils) {
        return new JwtFilter(jwtUtils);
    }

    @Bean
    public AccountRealm accountRealm(JwtUtils jwtUtils, CacheManager cacheManager) {
        return new AccountRealm(cacheManager, jwtUtils);
    }
    @Bean
    public JwtRealm jwtRealm(JwtUtils jwtUtils,CacheManager cacheManager){
        return new JwtRealm(cacheManager,jwtUtils);
    }


    @Bean
    public JwtDefaultSubjectFactory subjectFactory(){
        return new JwtDefaultSubjectFactory();
    }
    @Bean
    public IRedisManager redisManager() {
        return new RedisManager();
    }

    @Bean
    public RedisCacheManager redisCacheManager(IRedisManager redisManager) {
        RedisCacheManager redisCacheManager = new RedisCacheManager();
        redisCacheManager.setRedisManager(redisManager);
        return redisCacheManager;
    }

    /**
     * RedisSessionDAO shiro sessionDao层的实现 通过redis
     * 使用的是shiro-redis开源插件
     */
    @Bean
    public RedisSessionDAO redisSessionDAO() {
        RedisSessionDAO redisSessionDAO = new RedisSessionDAO();
        redisSessionDAO.setRedisManager(redisManager());
        redisSessionDAO.setExpire(10000000);
        return redisSessionDAO;
    }

    @Bean
    public DefaultWebSessionManager defaultWebSessionManager(RedisSessionDAO redisSessionDao) {
        DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
        sessionManager.setGlobalSessionTimeout(100000 * 1000);
        sessionManager.setDeleteInvalidSessions(true);
        sessionManager.setSessionDAO(redisSessionDao);
        sessionManager.setSessionValidationSchedulerEnabled(true);
        sessionManager.setDeleteInvalidSessions(true);
        /**
         * 修改Cookie中的SessionId的key，默认为JSESSIONID，自定义名称
         */
        sessionManager.setSessionIdCookie(new SimpleCookie("JSESSIONID"));
        /**
         * 这是因为 Shiro 跳转登录认证，302 重定向 URL 中带 JESSIONID 导致。
         *
         * 解决办法也很简单，禁止就行。
         */
        sessionManager.setSessionIdUrlRewritingEnabled(false);
        return sessionManager;
    }

    @Bean
    public DefaultWebSecurityManager securityManager(AccountRealm accountRealm,
        JwtRealm jwtRealm,
        CacheManager cacheManager,RedisSessionDAO redisSessionDao, JwtDefaultSubjectFactory subjectFactory) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setSubjectFactory(subjectFactory);
        //securityManager.setSessionManager(defaultWebSessionManager(redisSessionDao));
        //securityManager.setCacheManager(cacheManager);
        Collection<Realm> authorizingRealms=new ArrayList<>();
        authorizingRealms.add(accountRealm);
        authorizingRealms.add(jwtRealm);
        securityManager.setRealms(authorizingRealms);
        //subjectDAO.sessionStorageEvaluator.sessionStorageEnabled
        /*
         * 关闭shiro自带的session，详情见文档
         */
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator defaultSessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        defaultSessionStorageEvaluator.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(defaultSessionStorageEvaluator);
        securityManager.setSubjectDAO(subjectDAO);
        SecurityUtils.setSecurityManager(securityManager);
        return securityManager;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chainDefinition = new DefaultShiroFilterChainDefinition();
        chainDefinition.addPathDefinition("/favicon.ico", "anon");
        chainDefinition.addPathDefinition("/login", "anon");
        chainDefinition.addPathDefinition("/login.json", "anon");

        chainDefinition.addPathDefinition("/**", "jwt");
        return chainDefinition;
    }

    @Bean("shiroFilterFactoryBean")
    public ShiroFilterFactoryBean shiroFilterFactoryBean(SecurityManager securityManager,
        ShiroFilterChainDefinition shiroFilterChainDefinition, FilterRegistrationBean jwtFilterRegBean) {
        ShiroFilterFactoryBean shiroFilter = new ShiroFilterFactoryBean();
        shiroFilter.setSecurityManager(securityManager);
        shiroFilter.setLoginUrl("/login");
        shiroFilter.setUnauthorizedUrl("/403");
        Map<String, Filter> filters = new HashMap<>();
        filters.put("jwt", jwtFilterRegBean.getFilter());
        shiroFilter.setFilters(filters);
        Map<String, String> filterMap = shiroFilterChainDefinition.getFilterChainMap();
        shiroFilter.setFilterChainDefinitionMap(filterMap);
        return shiroFilter;
    }


    /**
     * 配置JwtFilter过滤器,并设置为未注册状态
     */
    @Bean
    public FilterRegistrationBean jwtFilterRegBean(JwtFilter jwtFilter) {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
        //添加JwtFilter  并设置为未注册状态
        filterRegistrationBean.setFilter(jwtFilter);
        filterRegistrationBean.setEnabled(false);
        return filterRegistrationBean;
    }
}
