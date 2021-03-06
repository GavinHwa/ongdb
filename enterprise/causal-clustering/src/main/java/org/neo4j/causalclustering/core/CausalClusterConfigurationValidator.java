/*
 * Copyright (c) 2018-2020 "Graph Foundation"
 * Graph Foundation, Inc. [https://graphfoundation.org]
 *
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of ONgDB Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) as found
 * in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
package org.neo4j.causalclustering.core;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;

import org.neo4j.causalclustering.routing.load_balancing.LoadBalancingPluginLoader;
import org.neo4j.graphdb.config.InvalidSettingException;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationValidator;
import org.neo4j.kernel.impl.enterprise.configuration.EnterpriseEditionSettings;
import org.neo4j.kernel.impl.enterprise.configuration.EnterpriseEditionSettings.Mode;
import org.neo4j.logging.Log;

import static org.neo4j.causalclustering.core.CausalClusteringSettings.minimum_core_cluster_size_at_runtime;
import static org.neo4j.causalclustering.core.CausalClusteringSettings.minimum_core_cluster_size_at_formation;

public class CausalClusterConfigurationValidator implements ConfigurationValidator
{
    @Override
    public Map<String,String> validate( @Nonnull Config config, @Nonnull Log log ) throws InvalidSettingException
    {
        // Make sure mode is CC
        Mode mode = config.get( EnterpriseEditionSettings.mode );
        if ( mode.equals( Mode.CORE ) || mode.equals( Mode.READ_REPLICA ) )
        {
            validateInitialDiscoveryMembers( config );
            validateBoltConnector( config );
            validateLoadBalancing( config, log );
            validateDeclaredClusterSizes( config );
        }

        return Collections.emptyMap();
    }

    private void validateDeclaredClusterSizes( Config config )
    {
        int startup = config.get( minimum_core_cluster_size_at_formation );
        int runtime = config.get( minimum_core_cluster_size_at_runtime );

        if ( runtime > startup )
        {
            throw new InvalidSettingException( String.format( "'%s' must be set greater than or equal to '%s'",
                    minimum_core_cluster_size_at_formation.name(), minimum_core_cluster_size_at_runtime.name() ) );
        }
    }

    private void validateLoadBalancing( Config config, Log log )
    {
        LoadBalancingPluginLoader.validate( config, log );
    }

    private void validateBoltConnector( Config config )
    {
        if ( config.enabledBoltConnectors().isEmpty() )
        {
            throw new InvalidSettingException( "A Bolt connector must be configured to run a cluster" );
        }
    }

    private void validateInitialDiscoveryMembers( Config config )
    {
        DiscoveryType discoveryType = config.get( CausalClusteringSettings.discovery_type );
        discoveryType.requiredSettings().forEach( setting -> {
            if ( !config.isConfigured( setting ) )
            {
                throw new InvalidSettingException( String.format( "Missing value for '%s', which is mandatory with '%s=%s'",
                        setting.name(), CausalClusteringSettings.discovery_type.name(), discoveryType ) );
            }
        } );
    }
}
