import os
from urllib.parse import urlparse

from dotenv import load_dotenv

load_dotenv()

def get_config() -> dict:
    web_url = urlparse(os.getenv('PUBLIC_WEB_URL'))
    return {
        'name': 'Temporal Latency Optimization',
        'temporal': {
            'connection': {
                'target': os.getenv('TEMPORAL_CONNECTION_TARGET'),
                'namespace': os.getenv('TEMPORAL_CONNECTION_NAMESPACE'),
                'mtls': {
                    'key_file': os.getenv('TEMPORAL_CONNECTION_MTLS_KEY_FILE'),
                    'cert_chain_file': os.getenv('TEMPORAL_CONNECTION_MTLS_CERT_CHAIN_FILE'),
                },
                'web_port': os.getenv('TEMPORAL_CONNECTION_WEB_PORT')
            },
            'worker': {
                'task_queue': os.getenv('TEMPORAL_TASK_QUEUE')
            },
            'api': {
                'port': os.getenv('CALLER_API_PORT')
            }
        },
        'web': {
            'url': web_url,
            'connection': {
                'mtls': {
                    'key_file': os.getenv('WEB_CONNECTION_MTLS_KEY_FILE'),
                    'cert_chain_file': os.getenv('WEB_CONNECTION_MTLS_CERT_CHAIN_FILE')
                }
            }
        }
    }