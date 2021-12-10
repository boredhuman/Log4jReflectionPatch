package dev.boredhuman.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.BaseConfiguration;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.selector.ContextSelector;
import org.apache.logging.log4j.spi.LoggerContextFactory;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class PatchLogger {

	static Field factoryField;
	static Field tempLookupField;
	static Field lookupField;
	static Field contextSelectorField;

	static {
		try {
			PatchLogger.factoryField = LogManager.class.getDeclaredField("factory");
			PatchLogger.factoryField.setAccessible(true);
			PatchLogger.tempLookupField = BaseConfiguration.class.getDeclaredField("tempLookup");
			PatchLogger.tempLookupField.setAccessible(true);
			PatchLogger.lookupField = Interpolator.class.getDeclaredField("lookups");
			PatchLogger.lookupField.setAccessible(true);
			PatchLogger.contextSelectorField = Log4jContextFactory.class.getDeclaredField("selector");
			PatchLogger.contextSelectorField.setAccessible(true);
		} catch (Throwable err) {
			err.printStackTrace();
		}
	}

	public static void patchLogger() {
		try {
			LoggerContextFactory contextFactory = (LoggerContextFactory) PatchLogger.factoryField.get(null);
			if (contextFactory instanceof Log4jContextFactory) {
				ContextSelector contextSelector = ((Log4jContextFactory) contextFactory).getSelector();
				List<LoggerContext> loggerContextList = contextSelector.getLoggerContexts();
				for (LoggerContext loggerContext : loggerContextList) {
					PatchLogger.sanitizeContext(loggerContext);
				}
				PatchLogger.contextSelectorField.set(contextFactory, new SafeWrappedContextSelector(contextSelector));
			}
		} catch (Throwable err) {
			err.printStackTrace();
		}
	}

	public static LoggerContext sanitizeContext(LoggerContext loggerContext) {
		if (loggerContext.getConfiguration() instanceof BaseConfiguration) {
			try {
				BaseConfiguration baseConfiguration = (BaseConfiguration) loggerContext.getConfiguration();
				Interpolator strLookup = (Interpolator) PatchLogger.tempLookupField.get(baseConfiguration);
				Map<String, StrLookup> map = (Map<String, StrLookup>) PatchLogger.lookupField.get(strLookup);
				map.clear();
				StrSubstitutor strSubstitutor = baseConfiguration.getStrSubstitutor();
				strLookup = (Interpolator) strSubstitutor.getVariableResolver();
				map = (Map<String, StrLookup>) PatchLogger.lookupField.get(strLookup);
				map.clear();
			} catch (Throwable err) {
				err.printStackTrace();
			}
		}
		return loggerContext;
	}

	public static class SafeWrappedContextSelector implements ContextSelector {

		ContextSelector contextSelector;

		public SafeWrappedContextSelector(ContextSelector contextSelector) {
			this.contextSelector = contextSelector;
		}

		@Override
		public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext) {
			return PatchLogger.sanitizeContext(this.contextSelector.getContext(fqcn, loader, currentContext));
		}

		@Override
		public LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext, URI configLocation) {
			return PatchLogger.sanitizeContext(this.contextSelector.getContext(fqcn, loader, currentContext, configLocation));
		}

		@Override
		public List<LoggerContext> getLoggerContexts() {
			return this.contextSelector.getLoggerContexts();
		}

		@Override
		public void removeContext(LoggerContext context) {
			this.contextSelector.removeContext(context);
		}
	}
}
