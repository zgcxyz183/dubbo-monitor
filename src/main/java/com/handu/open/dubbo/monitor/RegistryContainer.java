/**
 * Copyright 2006-2015 handu.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.handu.open.dubbo.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.RegistryService;
import com.handu.open.dubbo.monitor.dao.ConfigDAO;
import com.handu.open.dubbo.monitor.domain.AppAlarm;
import com.handu.open.dubbo.monitor.domain.AppItem;
import com.handu.open.dubbo.monitor.service.AppAlarmService;
import com.handu.open.dubbo.monitor.service.AppItemService;

/**
 * RegistryContainer
 *
 * @author Jinkai.Ma
 */
@Service
public class RegistryContainer {
	private static Logger logger = Logger.getLogger(RegistryContainer.class);

	public static final String REGISTRY_ADDRESS = "dubbo.registry.address";

	private final Set<String> applications = new ConcurrentHashSet<String>();

	private final Map<String, Set<String>> providerServiceApplications = new ConcurrentHashMap<String, Set<String>>();

	private final Map<String, Set<String>> providerApplicationServices = new ConcurrentHashMap<String, Set<String>>();

	private final Map<String, Set<String>> consumerServiceApplications = new ConcurrentHashMap<String, Set<String>>();

	private final Map<String, Set<String>> consumerApplicationServices = new ConcurrentHashMap<String, Set<String>>();

	private final Set<String> services = new ConcurrentHashSet<String>();

	private final Map<String, List<URL>> serviceProviders = new ConcurrentHashMap<String, List<URL>>();

	private final Map<String, List<URL>> serviceConsumers = new ConcurrentHashMap<String, List<URL>>();

	@Reference
	private RegistryService registry;
	@Autowired
	private AppItemService appItemService;
	@Autowired
	private AppAlarmService appAlarmService;
	@Autowired
	private ConfigDAO configDAO;

	public RegistryService getRegistry() {
		return registry;
	}

	public Set<String> getApplications() {
		return Collections.unmodifiableSet(applications);
	}

	public Set<String> getDependencies(String application, boolean reverse) {
		if (reverse) {
			Set<String> dependencies = new HashSet<String>();
			Set<String> services = providerApplicationServices.get(application);
			if (services != null && services.size() > 0) {
				for (String service : services) {
					Set<String> applications = consumerServiceApplications
							.get(service);
					if (applications != null && applications.size() > 0) {
						dependencies.addAll(applications);
					}
				}
			}
			return dependencies;
		} else {
			Set<String> dependencies = new HashSet<String>();
			Set<String> services = consumerApplicationServices.get(application);
			if (services != null && services.size() > 0) {
				for (String service : services) {
					Set<String> applications = providerServiceApplications
							.get(service);
					if (applications != null && applications.size() > 0) {
						dependencies.addAll(applications);
					}
				}
			}
			return dependencies;
		}
	}

	public Set<String> getServices() {
		return Collections.unmodifiableSet(services);
	}

	public Map<String, List<URL>> getServiceProviders() {
		return Collections.unmodifiableMap(serviceProviders);
	}

	public List<URL> getProvidersByService(String service) {
		List<URL> urls = serviceProviders.get(service);
		return urls == null ? null : Collections.unmodifiableList(urls);
	}

	public List<URL> getProvidersByHost(String host) {
		List<URL> urls = new ArrayList<URL>();
		if (host != null && host.length() > 0) {
			for (List<URL> providers : serviceProviders.values()) {
				for (URL url : providers) {
					if (host.equals(url.getHost())) {
						urls.add(url);
					}
				}
			}
		}
		return urls;
	}

	public List<URL> getProvidersByApplication(String application) {
		List<URL> urls = new ArrayList<URL>();
		if (application != null && application.length() > 0) {
			for (List<URL> providers : serviceProviders.values()) {
				for (URL url : providers) {
					if (application.equals(url
							.getParameter(Constants.APPLICATION_KEY))) {
						urls.add(url);
					}
				}
			}
		}
		return urls;
	}
	
	public AppItem getHostsByApplication(String application) {
		AppItem item = new AppItem();
		Set<String> addresses = new HashSet<String>();
		if (application != null && application.length() > 0) {
			for (List<URL> providers : serviceProviders.values()) {
				for (URL url : providers) {
					if (application.equals(url.getParameter(Constants.APPLICATION_KEY))) {
						addresses.add(url.getHost());
						if (item.getOwner() == null) {
							item.setOwner(url.getParameter("owner", "") + (url.hasParameter("organization")
									? " (" + url.getParameter("organization") + ")" : ""));
						}
					}
				}
			}
		}
		item.setProviderNum(addresses.size());
		StringBuilder buffer = new StringBuilder();
		for (String add : addresses) {
			buffer.append(add);
			buffer.append(",");
		}
		if (buffer.length() > 0) {
			buffer.deleteCharAt(buffer.length() - 1);
		}
		item.setProvider(buffer.toString());
		return item;
	}

	public Set<String> getHosts() {
		Set<String> addresses = new HashSet<String>();
		for (List<URL> providers : serviceProviders.values()) {
			for (URL url : providers) {
				addresses.add(url.getHost());
			}
		}
		for (List<URL> providers : serviceConsumers.values()) {
			for (URL url : providers) {
				addresses.add(url.getHost());
			}
		}
		return addresses;
	}

	public Map<String, List<URL>> getServiceConsumers() {
		return Collections.unmodifiableMap(serviceConsumers);
	}

	public List<URL> getConsumersByService(String service) {
		List<URL> urls = serviceConsumers.get(service);
		return urls == null ? null : Collections.unmodifiableList(urls);
	}

	public List<URL> getConsumersByHost(String host) {
		List<URL> urls = new ArrayList<URL>();
		if (host != null && host.length() > 0) {
			for (List<URL> consumers : serviceConsumers.values()) {
				for (URL url : consumers) {
					if (host.equals(url.getHost())) {
						urls.add(url);
					}
				}
			}
		}
		return Collections.unmodifiableList(urls);
	}

	public List<URL> getConsumersByApplication(String application) {
		List<URL> urls = new ArrayList<URL>();
		if (application != null && application.length() > 0) {
			for (List<URL> consumers : serviceConsumers.values()) {
				for (URL url : consumers) {
					if (application.equals(url
							.getParameter(Constants.APPLICATION_KEY))) {
						urls.add(url);
					}
				}
			}
		}
		return urls;
	}

	@PostConstruct
	public void start() {

		URL subscribeUrl = new URL(Constants.ADMIN_PROTOCOL,
				NetUtils.getLocalHost(), 0, "", Constants.INTERFACE_KEY,
				Constants.ANY_VALUE, Constants.GROUP_KEY, Constants.ANY_VALUE,
				Constants.VERSION_KEY, Constants.ANY_VALUE,
				Constants.CLASSIFIER_KEY, Constants.ANY_VALUE,
				Constants.CATEGORY_KEY, Constants.PROVIDERS_CATEGORY + ","
						+ Constants.CONSUMERS_CATEGORY, Constants.CHECK_KEY,
				String.valueOf(false));
		registry.subscribe(subscribeUrl, new NotifyListener() {
			public void notify(List<URL> urls) {
				if (urls == null || urls.size() == 0) {
					return;
				}
				Map<String, List<URL>> proivderMap = new HashMap<String, List<URL>>();
				Map<String, List<URL>> consumerMap = new HashMap<String, List<URL>>();
				Set<String> notifyApps = new HashSet<String>();
				for (URL url : urls) {
					logger.warn("notify url: " + url);
					String application = url
							.getParameter(Constants.APPLICATION_KEY);
					if (application != null && application.length() > 0) {
						applications.add(application);
						notifyApps.add(application);
					}
					String service = url.getServiceInterface();
					services.add(service);
					String category = url.getParameter(Constants.CATEGORY_KEY,
							Constants.DEFAULT_CATEGORY);
					if (Constants.PROVIDERS_CATEGORY.equals(category)) {
						if (Constants.EMPTY_PROTOCOL.equals(url.getProtocol())) {
							serviceProviders.remove(service);
						} else {
							List<URL> list = proivderMap.get(service);
							if (list == null) {
								list = new ArrayList<URL>();
								proivderMap.put(service, list);
							}
							list.add(url);
							if (application != null && application.length() > 0) {
								Set<String> serviceApplications = providerServiceApplications
										.get(service);
								if (serviceApplications == null) {
									providerServiceApplications.put(service,
											new ConcurrentHashSet<String>());
									serviceApplications = providerServiceApplications
											.get(service);
								}
								serviceApplications.add(application);

								Set<String> applicationServices = providerApplicationServices
										.get(application);
								if (applicationServices == null) {
									providerApplicationServices.put(
											application,
											new ConcurrentHashSet<String>());
									applicationServices = providerApplicationServices
											.get(application);
								}
								applicationServices.add(service);
							}
							initService(service,url);
						}
					} else if (Constants.CONSUMERS_CATEGORY.equals(category)) {
						if (Constants.EMPTY_PROTOCOL.equals(url.getProtocol())) {
							serviceConsumers.remove(service);
						} else {
							List<URL> list = consumerMap.get(service);
							if (list == null) {
								list = new ArrayList<URL>();
								consumerMap.put(service, list);
							}
							list.add(url);
							if (application != null && application.length() > 0) {
								Set<String> serviceApplications = consumerServiceApplications
										.get(service);
								if (serviceApplications == null) {
									consumerServiceApplications.put(service,
											new ConcurrentHashSet<String>());
									serviceApplications = consumerServiceApplications
											.get(service);
								}
								serviceApplications.add(application);

								Set<String> applicationServices = consumerApplicationServices
										.get(application);
								if (applicationServices == null) {
									consumerApplicationServices.put(
											application,
											new ConcurrentHashSet<String>());
									applicationServices = consumerApplicationServices
											.get(application);
								}
								applicationServices.add(service);
							}
							initService(service,url);
						}
					}
				}
				if (proivderMap != null && proivderMap.size() > 0) {
					serviceProviders.putAll(proivderMap);
					if(notifyApps.size()>0){
						appProviderChangeHandle(notifyApps);
					}					
				}
				if (consumerMap != null && consumerMap.size() > 0) {
					serviceConsumers.putAll(consumerMap);
				}
			}
		});

	}

	private void initService(String service, URL url) {
		if (service == null || service.isEmpty()) {
			return;
		}
		configDAO.getServiceId(service);
	}
	
	/**
	 * handle application provider count change.
	 * @param application
	 * @param url
	 */
	private void appProviderChangeHandle(Set<String> notifyApps) {
		if (notifyApps == null || notifyApps.isEmpty()) {
			return;
		}
		for (String application : notifyApps) {
			AppItem regItem = getHostsByApplication(application);
			AppItem existObj = appItemService.getByName(application);
			if (existObj == null) {
				AppItem item = new AppItem();
				item.setName(application);
				item.setProviderNum(regItem.getProviderNum() == 0 ? 1 : regItem.getProviderNum());
				item.setOwner(regItem.getOwner());
				item.setProvider(regItem.getProvider());
				appItemService.add(item);// id will load
				existObj = item;
			}
			if (existObj.getProviderNum() == regItem.getProviderNum()) {
				continue;
			}
			if (existObj.getProviderNum() > regItem.getProviderNum()) {
				AppAlarm alarm = new AppAlarm();
				alarm.setAppId(existObj.getId());
				alarm.setProviderNum(existObj.getProviderNum());
				alarm.setRegisterNum(regItem.getProviderNum());
				alarm.setInvokeTime(System.currentTimeMillis());
				appAlarmService.add(alarm);
			}
		}
	}

	@PreDestroy
	public void stop() {
	}
}